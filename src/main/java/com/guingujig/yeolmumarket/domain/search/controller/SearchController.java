package com.guingujig.yeolmumarket.domain.search.controller;

import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController {

  private final ProductService productService;

  @GetMapping("/products")
  public ResponseEntity<ApiResponse<PageResponse<ProductListItemResponse>>> searchProducts(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer minPrice,
      @RequestParam(required = false) Integer maxPrice,
      @RequestParam(defaultValue = "ON_SALE") ProductStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "latest") String sort) {
    PageResponse<ProductListItemResponse> response =
        productService.searchProducts(keyword, minPrice, maxPrice, status, page, size, sort);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
