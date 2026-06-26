package com.guingujig.yeolmumarket.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductSearchCacheEvictionListener {

  private static final Logger log =
      LoggerFactory.getLogger(ProductSearchCacheEvictionListener.class);

  private final CacheManager cacheManager;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void evictProductSearchCache(ProductSearchCacheEvictionEvent event) {
    Cache cache = cacheManager.getCache(SearchCacheNames.PRODUCT_SEARCH_V2);
    if (cache != null) {
      try {
        cache.invalidate();
      } catch (DataAccessException exception) {
        log.warn("상품 검색 캐시 무효화에 실패했습니다.", exception);
      }
    }
  }
}
