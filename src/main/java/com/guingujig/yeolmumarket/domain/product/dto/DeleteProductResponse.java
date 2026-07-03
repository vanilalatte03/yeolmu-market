package com.guingujig.yeolmumarket.domain.product.dto;

public record DeleteProductResponse(boolean deleted) {

  public static DeleteProductResponse success() {
    return new DeleteProductResponse(true);
  }
}
