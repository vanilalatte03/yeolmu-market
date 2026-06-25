package com.guingujig.yeolmumarket.domain.search.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record SearchProductResponse(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    String sellerNickname,
    OffsetDateTime createdAt) {

  public static SearchProductResponse from(Product product) {
    return new SearchProductResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        null,
        product.getSeller().getNickname(),
        product.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
