package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;

public record UpdateProductHiddenStatusResponse(
    Long productId, ProductStatus status, boolean hidden) {

  public static UpdateProductHiddenStatusResponse from(Product product) {
    return new UpdateProductHiddenStatusResponse(
        product.getId(), product.getStatus(), product.isHidden());
  }
}
