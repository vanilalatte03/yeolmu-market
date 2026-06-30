package com.guingujig.yeolmumarket.domain.product.dto;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.LocalDateTime;

public record ProductListItemProjection(
    Long productId,
    String title,
    Integer price,
    ProductStatus status,
    String thumbnailUrl,
    String sellerNickname,
    LocalDateTime createdAt) {}
