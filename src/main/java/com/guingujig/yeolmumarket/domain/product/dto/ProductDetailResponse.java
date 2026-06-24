package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ProductDetailResponse(
    Long productId,
    String title,
    String description,
    Integer price,
    ProductStatus status,
    SellerInfo seller,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static ProductDetailResponse from(Product product) {
    return new ProductDetailResponse(
        product.getId(),
        product.getTitle(),
        product.getDescription(),
        product.getPrice(),
        product.getStatus(),
        SellerInfo.from(product.getSeller()),
        product.getCreatedAt().atOffset(ZoneOffset.UTC),
        product.getModifiedAt().atOffset(ZoneOffset.UTC));
  }

  public record SellerInfo(Long userId, String nickname) {
    public static SellerInfo from(User seller) {
      return new SellerInfo(seller.getId(), seller.getNickname());
    }
  }
}
