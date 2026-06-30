package com.guingujig.yeolmumarket.domain.search.repository;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisPopularKeywordRepository implements PopularKeywordRepository {

  private static final String KEY_PREFIX = "search:popular-keywords:minute:";
  private static final String RECENT_AGGREGATE_KEY_PREFIX = "search:popular-keywords:recent:";

  private final StringRedisTemplate stringRedisTemplate;
  private final Clock clock;
  private final YeolmuProperties yeolmuProperties;

  @Override
  public void incrementSearchCount(String keyword) {
    String key = bucketKey(currentEpochMinute());
    stringRedisTemplate.opsForZSet().incrementScore(key, keyword, 1);
    stringRedisTemplate.expire(key, yeolmuProperties.search().popularKeywords().bucketTtl());
  }

  @Override
  public List<PopularKeyword> findTopKeywords(int limit) {
    if (limit <= 0) {
      return List.of();
    }

    long currentEpochMinute = currentEpochMinute();
    String recentAggregateKey = recentAggregateKey(currentEpochMinute);
    List<String> bucketKeys = recentBucketKeys(currentEpochMinute);
    stringRedisTemplate
        .opsForZSet()
        .unionAndStore(
            bucketKeys.get(0),
            bucketKeys.subList(1, bucketKeys.size()),
            recentAggregateKey,
            Aggregate.SUM);
    stringRedisTemplate.expire(
        recentAggregateKey, yeolmuProperties.search().popularKeywords().recentAggregateTtl());

    Set<TypedTuple<String>> tuples =
        stringRedisTemplate.opsForZSet().reverseRangeWithScores(recentAggregateKey, 0, limit - 1L);
    if (tuples == null || tuples.isEmpty()) {
      return List.of();
    }

    return tuples.stream()
        .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
        .map(tuple -> new PopularKeyword(tuple.getValue(), tuple.getScore().longValue()))
        .toList();
  }

  private List<String> recentBucketKeys(long currentEpochMinute) {
    return LongStream.range(0, yeolmuProperties.search().popularKeywords().recentWindowMinutes())
        .mapToObj(offset -> bucketKey(currentEpochMinute - offset))
        .toList();
  }

  private long currentEpochMinute() {
    return Instant.now(clock).getEpochSecond() / 60L;
  }

  private String bucketKey(long epochMinute) {
    return KEY_PREFIX + epochMinute;
  }

  private String recentAggregateKey(long epochMinute) {
    return RECENT_AGGREGATE_KEY_PREFIX + epochMinute;
  }
}
