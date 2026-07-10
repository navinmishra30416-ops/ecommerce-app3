package com.ecommerce.user.dto;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault
) {
}
