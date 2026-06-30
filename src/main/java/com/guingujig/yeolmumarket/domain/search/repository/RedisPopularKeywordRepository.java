package com.guingujig.yeolmumarket.domain.search.repository;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisPopularKeywordRepository implements PopularKeywordRepository {

  private static final String KEY_PREFIX = "search:popular-keywords:minute:";
  private static final int RECENT_WINDOW_MINUTES = 60;
  private static final Duration BUCKET_TTL = Duration.ofMinutes(RECENT_WINDOW_MINUTES + 10L);

  private final StringRedisTemplate stringRedisTemplate;
  private final Clock clock;

  @Override
  public void incrementSearchCount(String keyword) {
    String key = bucketKey(currentEpochMinute());
    stringRedisTemplate.opsForZSet().incrementScore(key, keyword, 1);
    stringRedisTemplate.expire(key, BUCKET_TTL);
  }

  @Override
  public List<PopularKeyword> findTopKeywords(int limit) {
    Map<String, Long> searchCounts = new HashMap<>();
    long currentEpochMinute = currentEpochMinute();
    for (int offset = 0; offset < RECENT_WINDOW_MINUTES; offset++) {
      collectSearchCounts(bucketKey(currentEpochMinute - offset), searchCounts);
    }

    return searchCounts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(limit)
        .map(entry -> new PopularKeyword(entry.getKey(), entry.getValue()))
        .toList();
  }

  private void collectSearchCounts(String key, Map<String, Long> searchCounts) {
    Set<TypedTuple<String>> tuples =
        stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
    if (tuples == null || tuples.isEmpty()) {
      return;
    }

    for (TypedTuple<String> tuple : tuples) {
      if (tuple.getValue() != null && tuple.getScore() != null) {
        searchCounts.merge(tuple.getValue(), tuple.getScore().longValue(), Long::sum);
      }
    }
  }

  private long currentEpochMinute() {
    return Instant.now(clock).getEpochSecond() / 60L;
  }

  private String bucketKey(long epochMinute) {
    return KEY_PREFIX + epochMinute;
  }
}
