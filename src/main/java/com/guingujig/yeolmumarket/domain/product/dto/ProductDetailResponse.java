package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ProductDetailResponse(
    Long productId,
    String title,
    String description,
    Integer price,
    ProductStatus status,
    long wishCount,
    boolean wished,
    SellerInfo seller,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static ProductDetailResponse from(Product product, ProductWishSummary wishSummary) {
    return new ProductDetailResponse(
        product.getId(),
        product.getTitle(),
        product.getDescription(),
        product.getPrice(),
        product.getStatus(),
        wishSummary.wishCount(),
        wishSummary.wished(),
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
