package com.guingujig.yeolmumarket.domain.category.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CategoryProductListItemResponse(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    String sellerNickname,
    OffsetDateTime createdAt) {

  public static CategoryProductListItemResponse from(Product product) {
    return from(product, null);
  }

  public static CategoryProductListItemResponse from(Product product, String thumbnailUrl) {
    return new CategoryProductListItemResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        thumbnailUrl,
        product.getSeller().getNickname(),
        product.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
