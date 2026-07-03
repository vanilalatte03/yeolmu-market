package com.guingujig.yeolmumarket.domain.wish.dto;

public record ProductWishSummary(Long productId, long wishCount, boolean wished) {

  public static ProductWishSummary empty(Long productId) {
    return new ProductWishSummary(productId, 0, false);
  }
}
