package com.guingujig.yeolmumarket.domain.category.dto;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import java.util.List;

public record GetCategoriesResponse(List<CategoryListItemResponse> categories) {

  public static GetCategoriesResponse from(List<Category> categories) {
    return new GetCategoriesResponse(
        categories.stream().map(CategoryListItemResponse::from).toList());
  }
}
