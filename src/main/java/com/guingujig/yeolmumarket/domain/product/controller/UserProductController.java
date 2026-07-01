package com.guingujig.yeolmumarket.domain.product.controller;

import com.guingujig.yeolmumarket.domain.product.dto.UserProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductFacade;
import com.guingujig.yeolmumarket.domain.product.service.SellerProductsQuery;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserProductController {

  private final ProductFacade productFacade;

  @GetMapping("/{userId}/products")
  public ResponseEntity<ApiResponse<PageResponse<UserProductListItemResponse>>> getUserProducts(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) ProductStatus status) {
    PageResponse<UserProductListItemResponse> response =
        productFacade.getPublicSellerProducts(new SellerProductsQuery(userId, page, size, status));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me/products")
  public ResponseEntity<ApiResponse<PageResponse<UserProductListItemResponse>>> getMyProducts(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) ProductStatus status) {
    PageResponse<UserProductListItemResponse> response =
        productFacade.getMyProducts(
            new SellerProductsQuery(authenticatedUser.userId(), page, size, status));
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
