package com.guingujig.yeolmumarket.domain.search.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.search.service.SearchProductDisplay;
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

  public static SearchProductResponse from(Product product, String thumbnailUrl) {
    return from(product, ProductWishSummary.empty(product.getId()), thumbnailUrl);
  }

  public static SearchProductResponse from(Product product, ProductWishSummary wishSummary) {
    return from(product, wishSummary, null);
  }

  public static SearchProductResponse from(
      Product product, ProductWishSummary wishSummary, String thumbnailUrl) {
    return new SearchProductResponse(
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

  public static SearchProductResponse from(
      SearchProductDisplay display, String sellerNickname, ProductWishSummary wishSummary) {
    return new SearchProductResponse(
        display.productId(),
        display.title(),
        display.price(),
        display.status(),
        display.thumbnailUrl(),
        sellerNickname,
        wishSummary.wishCount(),
        wishSummary.wished(),
        display.createdAt());
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
