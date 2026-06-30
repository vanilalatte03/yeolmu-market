package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CachedSearchProductQueryService {

  private final SearchProductQueryService searchProductQueryService;

  @Cacheable(cacheNames = SearchCacheNames.PRODUCT_SEARCH_LIST_V2, key = "#cacheKey")
  public PageResponse<Long> search(SearchProductCacheKey cacheKey) {
    return searchProductQueryService.searchProductIds(cacheKey.condition());
  }
}
