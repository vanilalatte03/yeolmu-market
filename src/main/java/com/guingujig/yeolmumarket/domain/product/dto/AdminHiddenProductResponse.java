package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record AdminHiddenProductResponse(
    Long productId,
    String title,
    ProductStatus status,
    boolean hidden,
    String sellerNickname,
    OffsetDateTime updatedAt) {

  public static AdminHiddenProductResponse from(Product product) {
    return new AdminHiddenProductResponse(
        product.getId(),
        product.getTitle(),
        product.getStatus(),
        product.isHidden(),
        product.getSeller().getNickname(),
        product.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
