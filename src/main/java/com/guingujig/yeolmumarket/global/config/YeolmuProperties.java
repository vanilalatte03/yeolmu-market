package com.guingujig.yeolmumarket.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu")
public record YeolmuProperties(Pagination pagination, Search search) {

  public YeolmuProperties {
    if (pagination == null) {
      pagination = new Pagination(null);
    }
    if (search == null) {
      search = new Search(null);
    }
  }

  public record Pagination(Integer maxPageSize) {

    private static final int DEFAULT_MAX_PAGE_SIZE = 100;

    public Pagination {
      if (maxPageSize == null) {
        maxPageSize = DEFAULT_MAX_PAGE_SIZE;
      }
      if (maxPageSize < 1) {
        throw new IllegalArgumentException("yeolmu.pagination.max-page-size는 1 이상이어야 합니다.");
      }
    }
  }

  public record Search(PopularKeywords popularKeywords) {

    public Search {
      if (popularKeywords == null) {
        popularKeywords = new PopularKeywords(null, null, null, null, null);
      }
    }
  }

  public record PopularKeywords(
      Integer defaultLimit,
      Integer maxLimit,
      Integer recentWindowMinutes,
      Duration bucketTtl,
      Duration recentAggregateTtl) {

    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_MAX_LIMIT = 50;
    private static final int DEFAULT_RECENT_WINDOW_MINUTES = 60;
    private static final Duration DEFAULT_BUCKET_TTL_PADDING = Duration.ofMinutes(10);
    private static final Duration DEFAULT_RECENT_AGGREGATE_TTL = Duration.ofSeconds(5);

    public PopularKeywords {
      if (defaultLimit == null) {
        defaultLimit = DEFAULT_LIMIT;
      }
      if (maxLimit == null) {
        maxLimit = DEFAULT_MAX_LIMIT;
      }
      if (recentWindowMinutes == null) {
        recentWindowMinutes = DEFAULT_RECENT_WINDOW_MINUTES;
      }
      if (defaultLimit < 1) {
        throw new IllegalArgumentException(
            "yeolmu.search.popular-keywords.default-limit은 1 이상이어야 합니다.");
      }
      if (maxLimit < defaultLimit) {
        throw new IllegalArgumentException(
            "yeolmu.search.popular-keywords.max-limit은 default-limit 이상이어야 합니다.");
      }
      if (recentWindowMinutes < 1) {
        throw new IllegalArgumentException(
            "yeolmu.search.popular-keywords.recent-window-minutes는 1 이상이어야 합니다.");
      }

      Duration recentWindow = Duration.ofMinutes(recentWindowMinutes);
      if (bucketTtl == null) {
        bucketTtl = recentWindow.plus(DEFAULT_BUCKET_TTL_PADDING);
      }
      if (bucketTtl.compareTo(recentWindow) < 0) {
        throw new IllegalArgumentException(
            "yeolmu.search.popular-keywords.bucket-ttl은 recent-window-minutes 이상이어야 합니다.");
      }
      if (recentAggregateTtl == null) {
        recentAggregateTtl = DEFAULT_RECENT_AGGREGATE_TTL;
      }
      if (recentAggregateTtl.isZero() || recentAggregateTtl.isNegative()) {
        throw new IllegalArgumentException(
            "yeolmu.search.popular-keywords.recent-aggregate-ttl은 0보다 커야 합니다.");
      }
    }
  }
}
