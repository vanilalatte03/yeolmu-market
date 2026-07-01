package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.entity.Product;

public record UpdateProductCommand(
    Product product, UpdateProductRequest request, Category category) {}
