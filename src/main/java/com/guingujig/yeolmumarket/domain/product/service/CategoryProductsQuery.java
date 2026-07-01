package com.guingujig.yeolmumarket.domain.product.service;

public record CategoryProductsQuery(Long categoryId, int page, int size, String sort) {}
