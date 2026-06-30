package com.guingujig.yeolmumarket.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductSearchCacheEventListener {

  private static final Logger log = LoggerFactory.getLogger(ProductSearchCacheEventListener.class);

  private final SearchIndexVersionProvider searchIndexVersionProvider;
  private final CacheManager cacheManager;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void increaseSearchIndexVersion(ProductSearchIndexChangedEvent event) {
    searchIndexVersionProvider.increaseVersions(event.affectedStatuses());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void evictProductDisplay(ProductDisplayChangedEvent event) {
    Cache cache = cacheManager.getCache(SearchCacheNames.PRODUCT_DISPLAY_V2);
    if (cache != null) {
      try {
        cache.evict(event.productId());
      } catch (RuntimeException exception) {
        log.warn("상품 표시 캐시 무효화에 실패했습니다. productId={}", event.productId(), exception);
      }
    }
  }
}
