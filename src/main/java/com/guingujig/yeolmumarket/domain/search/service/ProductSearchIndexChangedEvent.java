package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record ProductSearchIndexChangedEvent(Long productId, Set<ProductStatus> affectedStatuses) {

  public ProductSearchIndexChangedEvent(Long productId, ProductStatus... affectedStatuses) {
    this(productId, toPublicStatuses(affectedStatuses));
  }

  private static Set<ProductStatus> toPublicStatuses(ProductStatus... affectedStatuses) {
    return Arrays.stream(affectedStatuses)
        .filter(status -> status != null && status != ProductStatus.DELETED)
        .collect(Collectors.toUnmodifiableSet());
  }
}
