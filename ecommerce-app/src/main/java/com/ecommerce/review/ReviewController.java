package com.ecommerce.review;

import com.ecommerce.review.dto.ReviewRequest;
import com.ecommerce.review.dto.ReviewResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse create(@AuthenticationPrincipal UserDetails principal,
                                  @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(principal.getUsername(), request);
    }

    @GetMapping("/product/{productId}")
    public List<ReviewResponse> getForProduct(@PathVariable UUID productId) {
        return reviewService.getForProduct(productId);
    }
}
