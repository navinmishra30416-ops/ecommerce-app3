package com.ecommerce.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID productId,
        String userEmail,
        int rating,
        String comment,
        Instant createdAt
) {
}
