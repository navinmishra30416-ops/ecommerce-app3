package com.ecommerce.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        UUID categoryId,
        String categoryName,
        int availableQuantity,
        List<String> imageUrls
) {
}
