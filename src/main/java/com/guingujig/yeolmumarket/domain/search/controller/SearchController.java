package com.guingujig.yeolmumarket.domain.search.controller;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.search.dto.PopularKeywordsResponse;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.domain.search.service.PopularKeywordService;
import com.guingujig.yeolmumarket.domain.search.service.SearchService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController {

  private final SearchService searchService;
  private final PopularKeywordService popularKeywordService;

  @GetMapping("/products")
  public ResponseEntity<ApiResponse<PageResponse<SearchProductResponse>>> searchProducts(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer minPrice,
      @RequestParam(required = false) Integer maxPrice,
      @RequestParam(defaultValue = "ON_SALE") ProductStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "latest") String sort) {
    SearchProductRequest request =
        new SearchProductRequest(keyword, minPrice, maxPrice, status, page, size, sort);
    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request, resolveUserId(authenticatedUser));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/v2/products")
  public ResponseEntity<ApiResponse<PageResponse<SearchProductResponse>>> searchProductsV2(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer minPrice,
      @RequestParam(required = false) Integer maxPrice,
      @RequestParam(defaultValue = "ON_SALE") ProductStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "latest") String sort) {
    SearchProductRequest request =
        new SearchProductRequest(keyword, minPrice, maxPrice, status, page, size, sort);
    PageResponse<SearchProductResponse> response =
        searchService.searchProductsV2(request, resolveUserId(authenticatedUser));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/popular-keywords")
  public ResponseEntity<ApiResponse<PopularKeywordsResponse>> getPopularKeywords(
      @RequestParam(required = false) Integer limit) {
    PopularKeywordsResponse response = popularKeywordService.getPopularKeywords(limit);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long resolveUserId(AuthenticatedUser authenticatedUser) {
    return authenticatedUser == null ? null : authenticatedUser.userId();
  }
}
