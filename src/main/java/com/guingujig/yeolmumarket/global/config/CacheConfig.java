package com.guingujig.yeolmumarket.global.config;

import com.guingujig.yeolmumarket.domain.search.service.SearchCacheNames;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
@EnableConfigurationProperties(SearchCacheProperties.class)
public class CacheConfig implements CachingConfigurer {

  private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);
  private static final String SEARCH_CACHE_KEY_PREFIX = "cache:";

  @Bean
  public CacheManager cacheManager(
      RedisConnectionFactory redisConnectionFactory, SearchCacheProperties properties) {
    BasicPolymorphicTypeValidator cacheTypeValidator =
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.guingujig.yeolmumarket.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.util.")
            .build();
    RedisCacheConfiguration baseSearchCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .prefixCacheNameWith(SEARCH_CACHE_KEY_PREFIX)
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(cacheTypeValidator)
                        .build()));

    return RedisCacheManager.builder(redisConnectionFactory)
        .withInitialCacheConfigurations(
            Map.of(
                SearchCacheNames.PRODUCT_SEARCH_LIST_V2,
                baseSearchCacheConfiguration.entryTtl(properties.productsV2().listTtl()),
                SearchCacheNames.PRODUCT_DISPLAY_V2,
                baseSearchCacheConfiguration.entryTtl(properties.productsV2().displayTtl())))
        .disableCreateOnMissingCache()
        .build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        logCacheError("조회", exception, cache, key);
      }

      @Override
      public void handleCachePutError(
          RuntimeException exception, Cache cache, Object key, Object value) {
        logCacheError("저장", exception, cache, key);
      }

      @Override
      public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        logCacheError("삭제", exception, cache, key);
      }

      @Override
      public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("캐시 전체 무효화에 실패했습니다. cacheName={}", cache.getName(), exception);
      }
    };
  }

  private void logCacheError(
      String operation, RuntimeException exception, Cache cache, Object key) {
    log.warn("캐시 {}에 실패했습니다. cacheName={}, key={}", operation, cache.getName(), key, exception);
  }
}
