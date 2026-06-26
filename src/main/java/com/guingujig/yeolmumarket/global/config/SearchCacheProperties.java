package com.guingujig.yeolmumarket.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu.cache.search")
public record SearchCacheProperties(ProductsV2 productsV2) {

  public SearchCacheProperties {
    if (productsV2 == null) {
      productsV2 = new ProductsV2(null);
    }
  }

  public record ProductsV2(Duration ttl) {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public ProductsV2 {
      if (ttl == null) {
        ttl = DEFAULT_TTL;
      }
    }
  }
}
