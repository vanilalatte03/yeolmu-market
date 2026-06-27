package com.guingujig.yeolmumarket.domain.category.dto;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record UpdateCategoryResponse(Long categoryId, String name, OffsetDateTime updatedAt) {

  public static UpdateCategoryResponse from(Category category) {
    return new UpdateCategoryResponse(
        category.getId(), category.getName(), category.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
