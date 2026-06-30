package com.guingujig.yeolmumarket.domain.search.service;

import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisSearchIndexVersionProvider implements SearchIndexVersionProvider {

  private static final Logger log = LoggerFactory.getLogger(RedisSearchIndexVersionProvider.class);
  private static final String SEARCH_INDEX_VERSION_KEY_PREFIX = "search:index:version:";
  private static final String INITIAL_VERSION = "0";
  private static final String BYPASS_VERSION_PREFIX = "bypass:";

  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public String currentVersionKey(ProductStatus status) {
    try {
      String version = stringRedisTemplate.opsForValue().get(versionKey(status));
      if (version == null) {
        return INITIAL_VERSION;
      }
      return version;
    } catch (DataAccessException exception) {
      log.warn("상품 검색 인덱스 버전 조회에 실패해 검색 목록 캐시를 우회합니다. status={}", status, exception);
      return BYPASS_VERSION_PREFIX + UUID.randomUUID();
    }
  }

  @Override
  public void increaseVersions(Collection<ProductStatus> statuses) {
    for (ProductStatus status : statuses.stream().filter(Objects::nonNull).distinct().toList()) {
      try {
        stringRedisTemplate.opsForValue().increment(versionKey(status));
      } catch (DataAccessException exception) {
        log.warn("상품 검색 인덱스 버전 증가에 실패했습니다. status={}", status, exception);
      }
    }
  }

  private String versionKey(ProductStatus status) {
    return SEARCH_INDEX_VERSION_KEY_PREFIX + status.name();
  }
}
