package com.ecommerce.product;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.Inventory;
import com.ecommerce.inventory.InventoryRepository;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository,
                           CategoryRepository categoryRepository,
                           InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public Page<ProductResponse> listActive(UUID categoryId, String search, Pageable pageable) {
        Page<Product> page;
        if (search != null && !search.isBlank()) {
            page = productRepository.findByActiveTrueAndNameContainingIgnoreCase(search, pageable);
        } else if (categoryId != null) {
            page = productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findByActiveTrue(pageable);
        }
        return page.map(this::toResponse);
    }

    public ProductResponse getById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));
            product.setCategory(category);
        }

        productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(request.getInitialQuantity());
        inventory.setReserved(0);
        inventoryRepository.save(inventory);

        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));
            product.setCategory(category);
        }

        return toResponse(product);
    }

    @Transactional
    public void deactivate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setActive(false);
    }

    private ProductResponse toResponse(Product product) {
        int available = inventoryRepository.findById(product.getId())
                .map(Inventory::getAvailable)
                .orElse(0);

        List<String> imageUrls = product.getImages().stream()
                .map(ProductImage::getUrl)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                available,
                imageUrls
        );
    }
}
