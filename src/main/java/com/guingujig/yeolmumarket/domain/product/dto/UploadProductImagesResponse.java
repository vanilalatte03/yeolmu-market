package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import java.util.List;

public record UploadProductImagesResponse(List<ProductImageResponse> images) {

  public static UploadProductImagesResponse from(List<ProductImage> images) {
    return new UploadProductImagesResponse(
        images.stream().map(ProductImageResponse::from).toList());
  }
}
