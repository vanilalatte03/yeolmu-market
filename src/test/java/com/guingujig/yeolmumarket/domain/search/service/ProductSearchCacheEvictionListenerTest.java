package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ProductSearchCacheEvictionListenerTest {

  @Test
  void Redis_캐시_무효화가_실패해도_예외를_전파하지_않는다() {
    CacheManager cacheManager = mock(CacheManager.class);
    Cache cache = mock(Cache.class);
    ProductSearchCacheEvictionListener listener =
        new ProductSearchCacheEvictionListener(cacheManager);
    when(cacheManager.getCache(SearchCacheNames.PRODUCT_SEARCH_V2)).thenReturn(cache);
    doThrow(new DataAccessResourceFailureException("redis unavailable")).when(cache).invalidate();

    assertThatCode(() -> listener.evictProductSearchCache(new ProductSearchCacheEvictionEvent()))
        .doesNotThrowAnyException();

    verify(cache).invalidate();
  }
}
