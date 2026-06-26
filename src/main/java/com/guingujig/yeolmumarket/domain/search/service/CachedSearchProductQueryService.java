package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CachedSearchProductQueryService {

  private final SearchProductQueryService searchProductQueryService;

  @Cacheable(cacheNames = SearchCacheNames.PRODUCT_SEARCH_V2, key = "#condition")
  public PageResponse<SearchProductResponse> search(SearchProductCondition condition) {
    return searchProductQueryService.search(condition);
  }
}
