package com.ecommerce.cart;

import com.ecommerce.cart.dto.AddToCartRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.Inventory;
import com.ecommerce.inventory.InventoryRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        InventoryRepository inventoryRepository,
                        UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartResponse addItem(String userEmail, AddToCartRequest request) {
        Cart cart = getOrCreateCart(userEmail);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Inventory inventory = inventoryRepository.findById(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No inventory record for product: " + product.getId()));

        var existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + request.getQuantity();

        if (newQuantity > inventory.getAvailable()) {
            throw new BadRequestException("Only " + inventory.getAvailable() + " units of \"" + product.getName() + "\" are available");
        }

        CartItem item = existing.orElseGet(() -> {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            return newItem;
        });
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return getCart(userEmail);
    }

    @Transactional
    public CartResponse updateItemQuantity(String userEmail, UUID productId, int quantity) {
        Cart cart = getOrCreateCart(userEmail);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not in cart"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
            return getCart(userEmail);
        }

        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("No inventory record for product: " + productId));

        if (quantity > inventory.getAvailable()) {
            throw new BadRequestException("Only " + inventory.getAvailable() + " units available");
        }

        item.setQuantity(quantity);
        return getCart(userEmail);
    }

    @Transactional
    public CartResponse removeItem(String userEmail, UUID productId) {
        Cart cart = getOrCreateCart(userEmail);
        cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .ifPresent(cartItemRepository::delete);
        return getCart(userEmail);
    }

    public CartResponse getCart(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);

        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(i -> new CartItemResponse(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getProduct().getPrice(),
                        i.getQuantity(),
                        i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
                ))
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(itemResponses, total);
    }

    @Transactional
    public Cart getOrCreateCart(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    @Transactional
    public void clearCart(Cart cart) {
        cart.getItems().clear();
        cartRepository.save(cart);
    }
}
