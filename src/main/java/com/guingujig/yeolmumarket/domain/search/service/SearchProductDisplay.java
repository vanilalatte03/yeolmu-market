package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;

public record SearchProductDisplay(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    Long sellerId,
    OffsetDateTime createdAt) {}
