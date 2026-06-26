package com.guingujig.yeolmumarket.domain.search.dto;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;

public record SearchProductRequest(
    String keyword,
    Integer minPrice,
    Integer maxPrice,
    ProductStatus status,
    int page,
    int size,
    String sort) {}
