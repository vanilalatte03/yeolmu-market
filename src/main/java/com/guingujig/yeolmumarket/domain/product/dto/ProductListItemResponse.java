package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ProductListItemResponse(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    String sellerNickname,
    OffsetDateTime createdAt) {

  public static ProductListItemResponse from(Product product) {
    return new ProductListItemResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        null,
        product.getSeller().getNickname(),
        product.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
