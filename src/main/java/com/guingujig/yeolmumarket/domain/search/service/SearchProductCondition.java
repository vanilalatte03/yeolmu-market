package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record SearchProductCondition(
    String keyword,
    Integer minPrice,
    Integer maxPrice,
    ProductStatus status,
    int page,
    int size,
    String sort) {

  private static final int MAX_PAGE_SIZE = 100;
  private static final String DEFAULT_SORT = "latest";

  public static SearchProductCondition from(SearchProductRequest request) {
    validatePagination(request.page(), request.size());
    validatePriceRange(request.minPrice(), request.maxPrice());

    ProductStatus status = resolveStatus(request.status());
    validatePublicSearchStatus(status);
    String sort = normalizeSort(request.sort());
    validateSort(sort);

    return new SearchProductCondition(
        normalizeKeyword(request.keyword()),
        request.minPrice(),
        request.maxPrice(),
        status,
        request.page(),
        request.size(),
        sort);
  }

  public PageRequest toPageRequest() {
    return PageRequest.of(page, size, resolveSort(sort));
  }

  private static void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private static void validatePriceRange(Integer minPrice, Integer maxPrice) {
    if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "가격은 0 이상이어야 합니다.");
    }
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "최소 가격은 최대 가격보다 클 수 없습니다.");
    }
  }

  private static ProductStatus resolveStatus(ProductStatus status) {
    if (status == null) {
      return ProductStatus.ON_SALE;
    }
    return status;
  }

  private static void validatePublicSearchStatus(ProductStatus status) {
    if (status == ProductStatus.DELETED) {
      throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
    }
  }

  private static String normalizeKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return escapeLikeKeyword(keyword.trim());
  }

  private static String escapeLikeKeyword(String keyword) {
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private static String normalizeSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return DEFAULT_SORT;
    }
    return sort;
  }

  private static void validateSort(String sort) {
    if (!DEFAULT_SORT.equals(sort) && !"priceAsc".equals(sort) && !"priceDesc".equals(sort)) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 정렬 조건입니다.");
    }
  }

  private static Sort resolveSort(String sort) {
    return switch (sort) {
      case "priceAsc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
      case "priceDesc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
      default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    };
  }
}
