package com.guingujig.yeolmumarket.domain.product.service;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.user.entity.User;

public record CreateProductCommand(User seller, Category category, CreateProductRequest request) {}
