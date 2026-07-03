package com.guingujig.yeolmumarket.domain.category.dto;

public record DeleteCategoryResponse(boolean deleted) {

  public static DeleteCategoryResponse success() {
    return new DeleteCategoryResponse(true);
  }
}
