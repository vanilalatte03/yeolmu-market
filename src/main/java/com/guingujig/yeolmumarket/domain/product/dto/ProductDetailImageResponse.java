package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;

public record ProductDetailImageResponse(Long imageId, String url, boolean thumbnail) {

  public static ProductDetailImageResponse from(ProductImage image) {
    return new ProductDetailImageResponse(image.getId(), image.getUrl(), image.isThumbnail());
  }
}
