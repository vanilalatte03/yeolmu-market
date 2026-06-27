package com.guingujig.yeolmumarket.domain.search.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record SearchProductResponse(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    String sellerNickname,
    long wishCount,
    boolean wished,
    OffsetDateTime createdAt) {

  public static SearchProductResponse from(Product product) {
    return from(product, ProductWishSummary.empty(product.getId()));
  }

  public static SearchProductResponse from(Product product, ProductWishSummary wishSummary) {
    return new SearchProductResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        null,
        product.getSeller().getNickname(),
        wishSummary.wishCount(),
        wishSummary.wished(),
        product.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  public SearchProductResponse withWishSummary(ProductWishSummary wishSummary) {
    return new SearchProductResponse(
        productId,
        title,
        price,
        status,
        thumbnailUrl,
        sellerNickname,
        wishSummary.wishCount(),
        wishSummary.wished(),
        createdAt);
  }
}
