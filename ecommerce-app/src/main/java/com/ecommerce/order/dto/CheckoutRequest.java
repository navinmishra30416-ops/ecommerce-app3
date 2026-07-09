package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CheckoutRequest {

    @NotNull
    private UUID addressId;
}
