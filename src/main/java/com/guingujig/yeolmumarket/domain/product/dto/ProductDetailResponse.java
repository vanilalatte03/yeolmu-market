package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewRatingSummary;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record ProductDetailResponse(
    Long productId,
    String title,
    String description,
    Integer price,
    ProductStatus status,
    List<ProductDetailImageResponse> images,
    long wishCount,
    boolean wished,
    SellerInfo seller,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static ProductDetailResponse from(Product product, ProductWishSummary wishSummary) {
    return from(product, wishSummary, List.of(), ReviewRatingSummary.empty());
  }

  public static ProductDetailResponse from(
      Product product, ProductWishSummary wishSummary, ReviewRatingSummary sellerRatingSummary) {
    return from(product, wishSummary, List.of(), sellerRatingSummary);
  }

  public static ProductDetailResponse from(
      Product product,
      ProductWishSummary wishSummary,
      List<ProductImage> images,
      ReviewRatingSummary sellerRatingSummary) {
    return new ProductDetailResponse(
        product.getId(),
        product.getTitle(),
        product.getDescription(),
        product.getPrice(),
        product.getStatus(),
        images.stream().map(ProductDetailImageResponse::from).toList(),
        wishSummary.wishCount(),
        wishSummary.wished(),
        SellerInfo.from(product.getSeller(), sellerRatingSummary),
        product.getCreatedAt().atOffset(ZoneOffset.UTC),
        product.getModifiedAt().atOffset(ZoneOffset.UTC));
  }

  public record SellerInfo(Long userId, String nickname, Double averageRating) {
    public static SellerInfo from(User seller, ReviewRatingSummary ratingSummary) {
      return new SellerInfo(seller.getId(), seller.getNickname(), ratingSummary.averageRating());
    }
  }
}
