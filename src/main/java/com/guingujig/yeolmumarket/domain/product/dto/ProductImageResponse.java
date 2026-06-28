package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ProductImageResponse(
    Long imageId, String url, boolean thumbnail, OffsetDateTime uploadedAt) {

  public static ProductImageResponse from(ProductImage image) {
    return new ProductImageResponse(
        image.getId(),
        image.getUrl(),
        image.isThumbnail(),
        image.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
