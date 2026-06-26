package com.guingujig.yeolmumarket.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

@ExtendWith(MockitoExtension.class)
class RedisPopularKeywordRepositoryTest {

  private static final String KEY = "search:popular-keywords";

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private TypedTuple<String> firstTuple;
  @Mock private TypedTuple<String> secondTuple;

  private RedisPopularKeywordRepository repository;

  @BeforeEach
  void setUp() {
    repository = new RedisPopularKeywordRepository(stringRedisTemplate);
  }

  @Test
  void 검색어_검색횟수를_Sorted_Set에_누적한다() {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

    repository.incrementSearchCount("아이패드");

    verify(zSetOperations).incrementScore(KEY, "아이패드", 1);
  }

  @Test
  void 검색횟수_내림차순으로_상위_키워드를_조회한다() {
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(firstTuple.getValue()).thenReturn("아이패드");
    when(firstTuple.getScore()).thenReturn(3.0);
    when(secondTuple.getValue()).thenReturn("맥북");
    when(secondTuple.getScore()).thenReturn(2.0);
    Set<TypedTuple<String>> tuples = new LinkedHashSet<>(List.of(firstTuple, secondTuple));
    when(zSetOperations.reverseRangeWithScores(KEY, 0, 1)).thenReturn(tuples);

    List<PopularKeyword> popularKeywords = repository.findTopKeywords(2);

    assertThat(popularKeywords)
        .containsExactly(new PopularKeyword("아이패드", 3), new PopularKeyword("맥북", 2));
  }
}
