package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record UserProductListItemResponse(
    Long productId, String title, Integer price, ProductStatus status, OffsetDateTime createdAt) {

  public static UserProductListItemResponse from(Product product) {
    return new UserProductListItemResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        product.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
