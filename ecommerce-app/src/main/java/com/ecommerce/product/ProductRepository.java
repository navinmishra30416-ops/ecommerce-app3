package com.ecommerce.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryId(UUID categoryId, Pageable pageable);

    Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
}
