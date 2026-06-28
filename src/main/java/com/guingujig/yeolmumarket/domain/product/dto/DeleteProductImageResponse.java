package com.guingujig.yeolmumarket.domain.product.dto;

public record DeleteProductImageResponse(boolean deleted) {

  public static DeleteProductImageResponse success() {
    return new DeleteProductImageResponse(true);
  }
}
