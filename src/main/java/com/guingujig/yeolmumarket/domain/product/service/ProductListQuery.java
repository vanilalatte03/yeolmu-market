package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;

public record ProductListQuery(int page, int size, ProductStatus status, String sort) {}
