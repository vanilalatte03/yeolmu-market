package com.guingujig.yeolmumarket.domain.search.repository;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisPopularKeywordRepository implements PopularKeywordRepository {

  private static final String KEY = "search:popular-keywords";

  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public void incrementSearchCount(String keyword) {
    stringRedisTemplate.opsForZSet().incrementScore(KEY, keyword, 1);
  }

  @Override
  public List<PopularKeyword> findTopKeywords(int limit) {
    Set<TypedTuple<String>> tuples =
        stringRedisTemplate.opsForZSet().reverseRangeWithScores(KEY, 0, limit - 1L);
    if (tuples == null || tuples.isEmpty()) {
      return List.of();
    }

    return tuples.stream()
        .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
        .map(tuple -> new PopularKeyword(tuple.getValue(), tuple.getScore().longValue()))
        .toList();
  }
}
