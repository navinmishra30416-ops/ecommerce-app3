package com.ecommerce.order;

import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PaymentVerificationRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public OrderResponse checkout(@AuthenticationPrincipal UserDetails principal,
                                   @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(principal.getUsername(), request);
    }

    @PostMapping("/verify-payment")
    public OrderResponse verifyPayment(@AuthenticationPrincipal UserDetails principal,
                                        @Valid @RequestBody PaymentVerificationRequest request) {
        return orderService.verifyPayment(principal.getUsername(), request);
    }

    @GetMapping
    public List<OrderResponse> myOrders(@AuthenticationPrincipal UserDetails principal) {
        return orderService.getOrderHistory(principal.getUsername());
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.getById(id);
    }
}