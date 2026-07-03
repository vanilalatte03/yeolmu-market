package com.guingujig.yeolmumarket.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer score,
    @NotBlank @Size(max = 255, message = "리뷰 내용은 255자 이하여야 합니다.") String content) {}
