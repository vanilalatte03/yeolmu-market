package com.guingujig.yeolmumarket.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    @NotBlank(message = "카테고리명은 필수입니다.") @Size(max = 20, message = "카테고리명은 20자 이하여야 합니다.")
        String name) {}
