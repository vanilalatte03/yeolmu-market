package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;

public record SellerProductsQuery(Long sellerId, int page, int size, ProductStatus status) {}
