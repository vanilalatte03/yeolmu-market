package com.guingujig.yeolmumarket.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu.cache.search")
public record SearchCacheProperties(ProductsV2 productsV2) {

  public SearchCacheProperties {
    if (productsV2 == null) {
      productsV2 = new ProductsV2(null, null);
    }
  }

  public record ProductsV2(Duration ttl, Long maximumSize) {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final long DEFAULT_MAXIMUM_SIZE = 1_000L;

    public ProductsV2 {
      if (ttl == null) {
        ttl = DEFAULT_TTL;
      }
      if (maximumSize == null) {
        maximumSize = DEFAULT_MAXIMUM_SIZE;
      }
    }
  }
}
