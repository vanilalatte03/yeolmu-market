package com.guingujig.yeolmumarket.domain.product.controller;

import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductDetailResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ProductListItemResponse>>> getProducts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "ON_SALE") ProductStatus status,
      @RequestParam(defaultValue = "latest") String sort) {
    PageResponse<ProductListItemResponse> response =
        productService.getProducts(page, size, status, sort);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{productId}")
  public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(
      @PathVariable Long productId) {
    ProductDetailResponse response = productService.getProduct(productId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CreateProductResponse>> createProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @Valid @RequestBody CreateProductRequest request) {
    CreateProductResponse response =
        productService.createProduct(authenticatedUser.userId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/{productId}")
  public ResponseEntity<ApiResponse<UpdateProductResponse>> updateProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long productId,
      @Valid @RequestBody UpdateProductRequest request) {
    UpdateProductResponse response =
        productService.updateProduct(authenticatedUser.userId(), productId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{productId}")
  public ResponseEntity<ApiResponse<DeleteProductResponse>> deleteProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    DeleteProductResponse response =
        productService.deleteProduct(authenticatedUser.userId(), productId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
