package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ProductSearchCacheEventListenerTest {

  @Test
  void 검색_인덱스_변경_이벤트는_검색_버전을_증가시킨다() {
    SearchIndexVersionProvider searchIndexVersionProvider = mock(SearchIndexVersionProvider.class);
    CacheManager cacheManager = mock(CacheManager.class);
    ProductSearchCacheEventListener listener =
        new ProductSearchCacheEventListener(searchIndexVersionProvider, cacheManager);

    listener.increaseSearchIndexVersion(
        new ProductSearchIndexChangedEvent(1L, ProductStatus.ON_SALE, ProductStatus.RESERVED));

    verify(searchIndexVersionProvider)
        .increaseVersions(Set.of(ProductStatus.ON_SALE, ProductStatus.RESERVED));
  }

  @Test
  void 상품_표시_캐시_무효화가_실패해도_예외를_전파하지_않는다() {
    SearchIndexVersionProvider searchIndexVersionProvider = mock(SearchIndexVersionProvider.class);
    CacheManager cacheManager = mock(CacheManager.class);
    Cache cache = mock(Cache.class);
    ProductSearchCacheEventListener listener =
        new ProductSearchCacheEventListener(searchIndexVersionProvider, cacheManager);
    when(cacheManager.getCache(SearchCacheNames.PRODUCT_DISPLAY_V2)).thenReturn(cache);
    doThrow(new DataAccessResourceFailureException("redis unavailable")).when(cache).evict(1L);

    assertThatCode(() -> listener.evictProductDisplay(new ProductDisplayChangedEvent(1L)))
        .doesNotThrowAnyException();

    verify(cache).evict(1L);
  }
}
