package com.ecommerce.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ReviewRequest {

    @NotNull
    private UUID productId;

    @Min(1)
    @Max(5)
    private int rating;

    private String comment;
}
