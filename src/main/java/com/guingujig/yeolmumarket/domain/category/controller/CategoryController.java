package com.guingujig.yeolmumarket.domain.category.controller;

import com.guingujig.yeolmumarket.domain.category.dto.GetCategoriesResponse;
import com.guingujig.yeolmumarket.domain.category.service.CategoryService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  public ResponseEntity<ApiResponse<GetCategoriesResponse>> getCategories() {
    GetCategoriesResponse response = categoryService.getCategories();
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
