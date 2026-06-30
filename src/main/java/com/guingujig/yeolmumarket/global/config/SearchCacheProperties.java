package com.guingujig.yeolmumarket.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu.cache.search")
public record SearchCacheProperties(ProductsV2 productsV2) {

  public SearchCacheProperties {
    if (productsV2 == null) {
      productsV2 = new ProductsV2(null, null, null);
    }
  }

  public record ProductsV2(Duration ttl, Duration listTtl, Duration displayTtl) {

    private static final Duration DEFAULT_LIST_TTL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_DISPLAY_TTL = Duration.ofMinutes(5);

    public ProductsV2 {
      if (listTtl == null) {
        listTtl = ttl == null ? DEFAULT_LIST_TTL : ttl;
      }
      if (displayTtl == null) {
        displayTtl = ttl == null ? DEFAULT_DISPLAY_TTL : ttl;
      }
    }
  }
}
