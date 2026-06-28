package com.guingujig.yeolmumarket.domain.product.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
    @Pattern(regexp = ".*\\S.*", message = "상품명은 공백일 수 없습니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String title,
    @Pattern(regexp = ".*\\S.*", message = "상품 설명은 공백일 수 없습니다.") String description,
    @Positive(message = "판매 가격은 0보다 커야 합니다.") Integer price,
    @Positive(message = "카테고리 ID는 0보다 커야 합니다.") Long categoryId) {

  @AssertTrue(message = "수정할 값은 하나 이상이어야 합니다.")
  public boolean isUpdatableValuePresent() {
    return title != null || description != null || price != null || categoryId != null;
  }
}
