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
import com.ecommerce.payment.Payment;
import com.ecommerce.payment.PaymentService;
import com.ecommerce.payment.PaymentStatus;
import com.ecommerce.user.Address;
import com.ecommerce.user.AddressRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
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
     * Checkout flow:
     *  1. Lock inventory rows for every product in the cart (pessimistic write lock)
     *     so two concurrent checkouts can't both reserve the same last unit.
     *  2. Verify availability for every line before committing to any of them.
     *  3. Reserve stock, snapshot prices into order_items, create the order.
     *  4. Charge payment. On failure, the whole transaction rolls back —
     *     including the inventory reservation.
     *  5. Clear the cart only after everything succeeds.
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

        // Lock rows in a stable order (by product id) to avoid deadlocks
        // when two checkouts share overlapping products.
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

        Payment payment = paymentService.charge(order);

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            // Throwing here rolls back the transaction, releasing the
            // inventory reservation and the order automatically.
            throw new BadRequestException("Payment failed, order was not placed");
        }

        order.setStatus(OrderStatus.PAID);

        // Convert reservation into an actual stock decrement now that
        // payment succeeded, and clear the cart.
        for (CartItem item : sortedItems) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow();
            inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
            inventory.setReserved(inventory.getReserved() - item.getQuantity());
        }
        cartService.clearCart(cart);

        return toResponse(order);
    }

    public List<OrderResponse> getOrderHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse getById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getPriceAtPurchase()
                ))
                .toList();

        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(), items, order.getCreatedAt());
    }
}
