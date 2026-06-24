package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record UpdateProductResponse(
    Long productId,
    String title,
    String description,
    Integer price,
    ProductStatus status,
    OffsetDateTime updatedAt) {

  public static UpdateProductResponse from(Product product) {
    return new UpdateProductResponse(
        product.getId(),
        product.getTitle(),
        product.getDescription(),
        product.getPrice(),
        product.getStatus(),
        product.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
