package com.guingujig.yeolmumarket.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.guingujig.yeolmumarket.domain.search.service.SearchCacheNames;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(SearchCacheProperties.class)
public class CacheConfig {

  @Bean
  public CacheManager cacheManager(SearchCacheProperties properties) {
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(SearchCacheNames.PRODUCT_SEARCH_V2);
    cacheManager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(properties.productsV2().ttl())
            .maximumSize(properties.productsV2().maximumSize()));
    return cacheManager;
  }
}
