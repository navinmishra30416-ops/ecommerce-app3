package com.ecommerce.cart;

import com.ecommerce.cart.dto.AddToCartRequest;
import com.ecommerce.cart.dto.CartResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal UserDetails principal) {
        return cartService.getCart(principal.getUsername());
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(principal.getUsername(), request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable UUID productId,
                                    @RequestParam int quantity) {
        return cartService.updateItemQuantity(principal.getUsername(), productId, quantity);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable UUID productId) {
        return cartService.removeItem(principal.getUsername(), productId);
    }
}
