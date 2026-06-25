package com.guingujig.yeolmumarket.domain.product.controller;

import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusResponse;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class AdminProductController {

  private final ProductService productService;

  @PatchMapping("/{productId}/hidden")
  public ResponseEntity<ApiResponse<UpdateProductHiddenStatusResponse>> updateProductHiddenStatus(
      @PathVariable Long productId, @Valid @RequestBody UpdateProductHiddenStatusRequest request) {
    UpdateProductHiddenStatusResponse response =
        productService.updateProductHiddenStatus(productId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
