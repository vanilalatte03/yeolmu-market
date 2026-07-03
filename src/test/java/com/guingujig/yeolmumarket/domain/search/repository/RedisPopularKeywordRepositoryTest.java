package com.guingujig.yeolmumarket.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

@ExtendWith(MockitoExtension.class)
class RedisPopularKeywordRepositoryTest {

  private static final String KEY_PREFIX = "search:popular-keywords:minute:";
  private static final String RECENT_AGGREGATE_KEY_PREFIX = "search:popular-keywords:recent:";
  private static final Instant BASE_TIME = Instant.parse("2026-06-30T12:34:56Z");
  private static final Duration BUCKET_TTL = Duration.ofMinutes(70);
  private static final Duration RECENT_AGGREGATE_TTL = Duration.ofSeconds(5);

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private TypedTuple<String> firstTuple;
  @Mock private TypedTuple<String> secondTuple;

  private RedisPopularKeywordRepository repository;
  private Clock clock;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
    repository = new RedisPopularKeywordRepository(stringRedisTemplate, clock, yeolmuProperties());
  }

  @Test
  void 검색어_검색횟수를_현재_분_Sorted_Set에_누적하고_TTL을_설정한다() {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    String key = bucketKey(currentEpochMinute());

    repository.incrementSearchCount("아이패드");

    verify(zSetOperations).incrementScore(key, "아이패드", 1);
    verify(stringRedisTemplate).expire(key, BUCKET_TTL);
  }

  @Test
  void 최근_1시간_분단위_검색횟수를_Redis에서_합산해_상위_키워드를_조회한다() {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(firstTuple.getValue()).thenReturn("아이패드");
    when(firstTuple.getScore()).thenReturn(4.0);
    when(secondTuple.getValue()).thenReturn("맥북");
    when(secondTuple.getScore()).thenReturn(2.0);
    long currentEpochMinute = currentEpochMinute();
    Set<TypedTuple<String>> topTuples = new LinkedHashSet<>(List.of(firstTuple, secondTuple));
    when(zSetOperations.reverseRangeWithScores(recentAggregateKey(currentEpochMinute), 0, 1))
        .thenReturn(topTuples);

    List<PopularKeyword> popularKeywords = repository.findTopKeywords(2);

    assertThat(popularKeywords)
        .containsExactly(new PopularKeyword("아이패드", 4), new PopularKeyword("맥북", 2));
    verify(zSetOperations)
        .unionAndStore(
            eq(bucketKey(currentEpochMinute)),
            argThat(keys -> keys.size() == 59 && keys.contains(bucketKey(currentEpochMinute - 59))),
            eq(recentAggregateKey(currentEpochMinute)),
            eq(Aggregate.SUM));
    verify(stringRedisTemplate)
        .expire(recentAggregateKey(currentEpochMinute), RECENT_AGGREGATE_TTL);
  }

  @Test
  void Redis_ZSET_역순_범위_조회_순서를_그대로_반환한다() {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(firstTuple.getValue()).thenReturn("아이패드");
    when(firstTuple.getScore()).thenReturn(4.0);
    when(secondTuple.getValue()).thenReturn("맥북");
    when(secondTuple.getScore()).thenReturn(4.0);
    long currentEpochMinute = currentEpochMinute();
    Set<TypedTuple<String>> topTuples = new LinkedHashSet<>(List.of(firstTuple, secondTuple));
    when(zSetOperations.reverseRangeWithScores(recentAggregateKey(currentEpochMinute), 0, 1))
        .thenReturn(topTuples);

    List<PopularKeyword> popularKeywords = repository.findTopKeywords(2);

    assertThat(popularKeywords)
        .containsExactly(new PopularKeyword("아이패드", 4), new PopularKeyword("맥북", 4));
  }

  @Test
  void 최근_1시간_밖의_분단위_검색횟수는_Redis_합산_대상에서_제외한다() {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(firstTuple.getValue()).thenReturn("아이패드");
    when(firstTuple.getScore()).thenReturn(1.0);
    when(zSetOperations.reverseRangeWithScores(recentAggregateKey(currentEpochMinute()), 0, 9))
        .thenReturn(new LinkedHashSet<>(List.of(firstTuple)));

    List<PopularKeyword> popularKeywords = repository.findTopKeywords(10);

    assertThat(popularKeywords).containsExactly(new PopularKeyword("아이패드", 1));
    verify(zSetOperations)
        .unionAndStore(
            eq(bucketKey(currentEpochMinute())),
            argThat(
                keys ->
                    keys.size() == 59
                        && keys.contains(bucketKey(currentEpochMinute() - 59))
                        && !keys.contains(bucketKey(currentEpochMinute() - 60))),
            eq(recentAggregateKey(currentEpochMinute())),
            eq(Aggregate.SUM));
  }

  private long currentEpochMinute() {
    return BASE_TIME.getEpochSecond() / 60L;
  }

  private String bucketKey(long epochMinute) {
    return KEY_PREFIX + epochMinute;
  }

  private String recentAggregateKey(long epochMinute) {
    return RECENT_AGGREGATE_KEY_PREFIX + epochMinute;
  }

  private YeolmuProperties yeolmuProperties() {
    return new YeolmuProperties(
        null,
        new YeolmuProperties.Search(
            new YeolmuProperties.PopularKeywords(10, 50, 60, BUCKET_TTL, RECENT_AGGREGATE_TTL)));
  }
}
