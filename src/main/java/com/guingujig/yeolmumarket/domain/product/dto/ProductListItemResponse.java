package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ProductListItemResponse(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    String sellerNickname,
    long wishCount,
    boolean wished,
    OffsetDateTime createdAt) {

  public static ProductListItemResponse from(Product product, ProductWishSummary wishSummary) {
    return from(product, wishSummary, null);
  }

  public static ProductListItemResponse from(
      Product product, ProductWishSummary wishSummary, String thumbnailUrl) {
    return new ProductListItemResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        thumbnailUrl,
        product.getSeller().getNickname(),
        wishSummary.wishCount(),
        wishSummary.wished(),
        product.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
