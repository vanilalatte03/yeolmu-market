package com.guingujig.yeolmumarket.domain.product.repository;

import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;

public interface ProductImageRepositoryCustom {

  void deleteAndPromoteNextThumbnail(ProductImage image);
}
