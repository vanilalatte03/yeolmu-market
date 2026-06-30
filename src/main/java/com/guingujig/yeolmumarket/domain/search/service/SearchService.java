package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.domain.user.service.UserNicknameQueryService;
import com.guingujig.yeolmumarket.domain.wish.dto.ProductWishSummary;
import com.guingujig.yeolmumarket.domain.wish.service.ProductWishSummaryService;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  private final SearchProductQueryService searchProductQueryService;
  private final CachedSearchProductQueryService cachedSearchProductQueryService;
  private final ProductDisplayQueryService productDisplayQueryService;
  private final SearchIndexVersionProvider searchIndexVersionProvider;
  private final PopularKeywordService popularKeywordService;
  private final UserNicknameQueryService userNicknameQueryService;
  private final ProductWishSummaryService productWishSummaryService;

  /**
   * 공개 상품을 키워드, 가격 범위, 상품 상태 조건으로 검색한다.
   *
   * <p>유효한 키워드는 Redis 인기 검색어 집계에 반영한다. Redis 집계 실패는 검색 결과 반환을 막지 않는다.
   */
  @Transactional(readOnly = true)
  public PageResponse<SearchProductResponse> searchProducts(SearchProductRequest request) {
    return searchProducts(request, null);
  }

  @Transactional(readOnly = true)
  public PageResponse<SearchProductResponse> searchProducts(
      SearchProductRequest request, Long authenticatedUserId) {
    SearchProductCondition condition = SearchProductCondition.from(request);
    recordSearchKeywordSafely(request.keyword());
    return assembleSearchResponse(
        searchProductQueryService.searchProductIds(condition), authenticatedUserId);
  }

  /**
   * v1과 같은 검색 계약을 유지하면서 정규화된 검색 조건별로 Redis 캐시를 사용한다.
   *
   * <p>인기 검색어 집계는 캐시 대상 메서드 밖에서 먼저 수행해 캐시 hit 상황에서도 요청마다 반영한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<SearchProductResponse> searchProductsV2(SearchProductRequest request) {
    return searchProductsV2(request, null);
  }

  @Transactional(readOnly = true)
  public PageResponse<SearchProductResponse> searchProductsV2(
      SearchProductRequest request, Long authenticatedUserId) {
    SearchProductCondition condition = SearchProductCondition.from(request);
    recordSearchKeywordSafely(request.keyword());
    SearchProductCacheKey cacheKey =
        new SearchProductCacheKey(condition, searchIndexVersionProvider.currentVersionKey());
    return assembleSearchResponse(
        cachedSearchProductQueryService.search(cacheKey), authenticatedUserId);
  }

  private PageResponse<SearchProductResponse> assembleSearchResponse(
      PageResponse<Long> response, Long authenticatedUserId) {
    List<SearchProductDisplay> displays =
        productDisplayQueryService.getDisplays(response.content());
    List<Long> productIds = displays.stream().map(SearchProductDisplay::productId).toList();
    Map<Long, String> sellerNicknames =
        userNicknameQueryService.getNicknames(
            displays.stream().map(SearchProductDisplay::sellerId).toList());
    Map<Long, ProductWishSummary> wishSummaries =
        productWishSummaryService.getSummaries(productIds, authenticatedUserId);
    List<SearchProductResponse> content =
        displays.stream()
            .map(
                display ->
                    SearchProductResponse.from(
                        display,
                        sellerNicknames.get(display.sellerId()),
                        wishSummaries.getOrDefault(
                            display.productId(), ProductWishSummary.empty(display.productId()))))
            .toList();

    return new PageResponse<>(
        content,
        response.page(),
        response.size(),
        response.totalElements(),
        response.totalPages(),
        response.hasNext());
  }

  private void recordSearchKeywordSafely(String keyword) {
    try {
      popularKeywordService.recordSearchKeyword(keyword);
    } catch (DataAccessException exception) {
      log.warn("인기 검색어 집계에 실패했습니다.", exception);
    }
  }
}
