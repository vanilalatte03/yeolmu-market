package com.guingujig.yeolmumarket.domain.category.dto;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CreateCategoryResponse(Long categoryId, String name, OffsetDateTime createdAt) {

  public static CreateCategoryResponse from(Category category) {
    return new CreateCategoryResponse(
        category.getId(), category.getName(), category.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
