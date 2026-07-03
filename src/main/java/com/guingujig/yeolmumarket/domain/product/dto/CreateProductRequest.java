package com.guingujig.yeolmumarket.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
    @NotBlank(message = "상품명은 필수입니다.") @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String title,
    @NotBlank(message = "상품 설명은 필수입니다.") String description,
    @NotNull(message = "판매 가격은 필수입니다.") @Positive(message = "판매 가격은 0보다 커야 합니다.") Integer price,
    @NotNull(message = "카테고리는 필수입니다.") @Positive(message = "카테고리 ID는 0보다 커야 합니다.")
        Long categoryId) {}
