package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

  private static final int MAX_PAGE_SIZE = 100;

  private final ProductRepository productRepository;

  /**
   * 공개 상품을 키워드, 가격 범위, 상품 상태 조건으로 검색한다.
   *
   * <p>P0 검색은 DB 조회만 수행하며 Redis 검색 캐시나 인기 검색어 집계는 갱신하지 않는다.
   */
  @Transactional(readOnly = true)
  public PageResponse<SearchProductResponse> searchProducts(SearchProductRequest request) {
    validatePagination(request.page(), request.size());
    validatePriceRange(request.minPrice(), request.maxPrice());

    ProductStatus status = resolveStatus(request.status());
    validatePublicSearchStatus(status);

    Page<Product> products =
        productRepository.searchPublicProducts(
            normalizeKeyword(request.keyword()),
            request.minPrice(),
            request.maxPrice(),
            status,
            PageRequest.of(request.page(), request.size(), resolveSort(request.sort())));

    return PageResponse.from(products.map(SearchProductResponse::from));
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private void validatePriceRange(Integer minPrice, Integer maxPrice) {
    if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "가격은 0 이상이어야 합니다.");
    }
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "최소 가격은 최대 가격보다 클 수 없습니다.");
    }
  }

  private ProductStatus resolveStatus(ProductStatus status) {
    if (status == null) {
      return ProductStatus.ON_SALE;
    }
    return status;
  }

  private void validatePublicSearchStatus(ProductStatus status) {
    if (status == ProductStatus.DELETED) {
      throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
    }
  }

  private String normalizeKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return keyword.trim();
  }

  private Sort resolveSort(String sort) {
    if (sort == null || sort.isBlank() || sort.equals("latest")) {
      return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }
    return switch (sort) {
      case "priceAsc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
      case "priceDesc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
      default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 정렬 조건입니다.");
    };
  }
}
