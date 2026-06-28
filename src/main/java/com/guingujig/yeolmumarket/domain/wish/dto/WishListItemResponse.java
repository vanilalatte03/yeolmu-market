package com.guingujig.yeolmumarket.domain.wish.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record WishListItemResponse(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    OffsetDateTime wishedAt) {

  public static WishListItemResponse from(Wish wish) {
    return from(wish, null);
  }

  public static WishListItemResponse from(Wish wish, String thumbnailUrl) {
    Product product = wish.getProduct();
    return new WishListItemResponse(
        product.getId(),
        product.getTitle(),
        product.getPrice(),
        product.getStatus(),
        thumbnailUrl,
        wish.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
