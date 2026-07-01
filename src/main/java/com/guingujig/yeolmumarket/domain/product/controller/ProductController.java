package com.guingujig.yeolmumarket.domain.product.controller;

import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductImageResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductDetailResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UploadProductImagesResponse;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductFacade;
import com.guingujig.yeolmumarket.domain.product.service.ProductImageService;
import com.guingujig.yeolmumarket.domain.product.service.ProductListQuery;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

  private final ProductFacade productFacade;
  private final ProductImageService productImageService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ProductListItemResponse>>> getProducts(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "ON_SALE") ProductStatus status,
      @RequestParam(defaultValue = "latest") String sort) {
    PageResponse<ProductListItemResponse> response =
        productFacade.getProducts(
            new ProductListQuery(page, size, status, sort), resolveUserId(authenticatedUser));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{productId}")
  public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    ProductDetailResponse response =
        productFacade.getProduct(productId, resolveUserId(authenticatedUser));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CreateProductResponse>> createProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @Valid @RequestBody CreateProductRequest request) {
    CreateProductResponse response =
        productFacade.createProduct(authenticatedUser.userId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping(path = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<UploadProductImagesResponse>> uploadProductImages(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long productId,
      @RequestPart("images") List<MultipartFile> images) {
    UploadProductImagesResponse response =
        productImageService.uploadImages(authenticatedUser.userId(), productId, images);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/{productId}")
  public ResponseEntity<ApiResponse<UpdateProductResponse>> updateProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long productId,
      @Valid @RequestBody UpdateProductRequest request) {
    UpdateProductResponse response =
        productFacade.updateProduct(authenticatedUser.userId(), productId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{productId}")
  public ResponseEntity<ApiResponse<DeleteProductResponse>> deleteProduct(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    DeleteProductResponse response =
        productFacade.deleteProduct(authenticatedUser.userId(), productId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{productId}/images/{imageId}")
  public ResponseEntity<ApiResponse<DeleteProductImageResponse>> deleteProductImage(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long productId,
      @PathVariable Long imageId) {
    DeleteProductImageResponse response =
        productImageService.deleteImage(authenticatedUser.userId(), productId, imageId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long resolveUserId(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null) {
      return null;
    }
    return authenticatedUser.userId();
  }
}
