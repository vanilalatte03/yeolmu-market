package com.guingujig.yeolmumarket.domain.category.controller;

import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.CreateCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.dto.DeleteCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryRequest;
import com.guingujig.yeolmumarket.domain.category.dto.UpdateCategoryResponse;
import com.guingujig.yeolmumarket.domain.category.service.CategoryService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

  private final CategoryService categoryService;

  @PostMapping
  public ResponseEntity<ApiResponse<CreateCategoryResponse>> createCategory(
      @Valid @RequestBody CreateCategoryRequest request) {
    CreateCategoryResponse response = categoryService.createCategory(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/{categoryId}")
  public ResponseEntity<ApiResponse<UpdateCategoryResponse>> updateCategory(
      @PathVariable Long categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
    UpdateCategoryResponse response = categoryService.updateCategory(categoryId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{categoryId}")
  public ResponseEntity<ApiResponse<DeleteCategoryResponse>> deleteCategory(
      @PathVariable Long categoryId) {
    DeleteCategoryResponse response = categoryService.deleteCategory(categoryId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
