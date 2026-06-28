package com.guingujig.yeolmumarket.domain.category.controller;

import com.guingujig.yeolmumarket.domain.category.dto.CategoryProductListItemResponse;
import com.guingujig.yeolmumarket.domain.category.dto.GetCategoriesResponse;
import com.guingujig.yeolmumarket.domain.category.service.CategoryService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/{categoryId}/products")
  public ResponseEntity<ApiResponse<PageResponse<CategoryProductListItemResponse>>>
      getCategoryProducts(
          @PathVariable Long categoryId,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "10") int size,
          @RequestParam(defaultValue = "latest") String sort) {
    PageResponse<CategoryProductListItemResponse> response =
        categoryService.getCategoryProducts(categoryId, page, size, sort);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
