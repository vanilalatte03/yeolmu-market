package com.guingujig.yeolmumarket.domain.category.dto;

import com.guingujig.yeolmumarket.domain.category.entity.Category;

public record CategoryListItemResponse(Long categoryId, String name) {

  public static CategoryListItemResponse from(Category category) {
    return new CategoryListItemResponse(category.getId(), category.getName());
  }
}
