package com.guingujig.yeolmumarket.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductSearchCacheEvictionListener {

  private final CacheManager cacheManager;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void evictProductSearchCache(ProductSearchCacheEvictionEvent event) {
    Cache cache = cacheManager.getCache(SearchCacheNames.PRODUCT_SEARCH_V2);
    if (cache != null) {
      cache.clear();
    }
  }
}
