package com.ecommerce.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findByProductId(UUID productId);
    boolean existsByProductIdAndUserId(UUID productId, UUID userId);
}
