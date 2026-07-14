package com.ecommerce.order;

import com.ecommerce.cart.Cart;
import com.ecommerce.cart.CartItem;
import com.ecommerce.cart.CartService;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.Inventory;
import com.ecommerce.inventory.InventoryRepository;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PaymentVerificationRequest;
import com.ecommerce.payment.Payment;
import com.ecommerce.payment.PaymentService;
import com.ecommerce.payment.PaymentStatus;
import com.ecommerce.user.Address;
import com.ecommerce.user.AddressRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final InventoryRepository inventoryRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    public OrderService(OrderRepository orderRepository,
                         CartService cartService,
                         InventoryRepository inventoryRepository,
                         AddressRepository addressRepository,
                         UserRepository userRepository,
                         PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.inventoryRepository = inventoryRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
    }

    /**
     * Checkout flow (Part 1 — reservation):
     *  1. Lock inventory rows for every product in the cart so two concurrent
     *     checkouts can't both reserve the same last unit.
     *  2. Verify availability for every line before committing to any of them.
     *  3. Reserve stock, snapshot prices into order_items, create the order as PENDING.
     *  4. Create a Razorpay order and return its id + the public key so the
     *     frontend can open the Razorpay Checkout popup.
     *
     *  The order becomes PAID only later, via verifyPayment(), once Razorpay
     *  confirms the payment with a valid signature.
     */
    @Transactional
    public OrderResponse checkout(String userEmail, CheckoutRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Address does not belong to this user");
        }

        Cart cart = cartService.getOrCreateCart(userEmail);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;

        List<CartItem> sortedItems = cart.getItems().stream()
                .sorted((a, b) -> a.getProduct().getId().compareTo(b.getProduct().getId()))
                .toList();

        for (CartItem item : sortedItems) {
            UUID productId = item.getProduct().getId();

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("No inventory for product: " + productId));

            if (item.getQuantity() > inventory.getAvailable()) {
                throw new BadRequestException(
                        "\"" + item.getProduct().getName() + "\" only has " + inventory.getAvailable() + " units available");
            }

            inventory.setReserved(inventory.getReserved() + item.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(item.getProduct());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPriceAtPurchase(item.getProduct().getPrice());
            order.getItems().add(orderItem);

            total = total.add(item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(total);
        orderRepository.save(order);

        // Create the Razorpay order now — the frontend needs its id to open Checkout.
        Payment payment = paymentService.createRazorpayOrder(order);

        return toResponse(order, payment.getTransactionRef(), razorpayKeyId);
    }

    /**
     * Checkout flow (Part 2 — confirmation):
     * Called by the frontend after the Razorpay Checkout popup reports success.
     * Verifies the signature server-side before trusting the payment at all.
     * On success: converts the inventory reservation into a real stock
     * decrement, marks the order PAID, and clears the cart.
     * On failure: releases the reservation and marks the order CANCELLED.
     */
    @Transactional
    public OrderResponse verifyPayment(String userEmail, PaymentVerificationRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        boolean valid = paymentService.verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
    
        );

        /**
     * Called after signature verification succeeds. Updates the Payment
     * record from PENDING to SUCCEEDED, and swaps the stored reference
     * from the Razorpay order id to the actual Razorpay payment id.
     */
    public void markSucceeded(UUID orderId, String razorpayPaymentId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setTransactionRef(razorpayPaymentId);
        paymentRepository.save(payment);
    }

        if (!valid) {
            releaseReservation(order);
            order.setStatus(OrderStatus.CANCELLED);
            throw new BadRequestException("Payment verification failed, order was cancelled");
        }

        paymentService.markSucceeded(request.getOrderId(), request.getRazorpayPaymentId());

        order.setStatus(OrderStatus.PAID);

        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow();
            inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
            inventory.setReserved(inventory.getReserved() - item.getQuantity());
        }

        Cart cart = cartService.getOrCreateCart(userEmail);
        cartService.clearCart(cart);

        return toResponse(order, null, null);
    }

    private void releaseReservation(Order order) {
        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow();
            inventory.setReserved(inventory.getReserved() - item.getQuantity());
        }
    }

    public List<OrderResponse> getOrderHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(o -> toResponse(o, null, null))
                .toList();
    }

    public OrderResponse getById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return toResponse(order, null, null);
    }

    private OrderResponse toResponse(Order order, String razorpayOrderId, String razorpayKeyId) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getPriceAtPurchase()
                ))
                .toList();

        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(), items,
                order.getCreatedAt(), razorpayOrderId, razorpayKeyId);
    }
}