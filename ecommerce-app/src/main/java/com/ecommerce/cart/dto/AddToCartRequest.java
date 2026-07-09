package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AddToCartRequest {

    @NotNull
    private UUID productId;

    @Min(1)
    private int quantity = 1;
}
