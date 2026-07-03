package com.guingujig.yeolmumarket.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRevokedAccessTokenRepositoryTest {

  private static final String HASH = "test-hash";
  private static final String KEY = "auth:revoked:access:test-hash";

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisRevokedAccessTokenRepository repository;

  @BeforeEach
  void setUp() {
    repository = new RedisRevokedAccessTokenRepository(stringRedisTemplate);
  }

  @Test
  void access_token_해시를_TTL과_함께_블랙리스트에_등록한다() {
    Duration ttl = Duration.ofSeconds(3600);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    repository.add(HASH, ttl);

    verify(valueOperations).set(KEY, "1", ttl);
  }

  @Test
  void 블랙리스트에_등록된_해시가_존재하면_true를_반환한다() {
    when(stringRedisTemplate.hasKey(KEY)).thenReturn(true);

    assertThat(repository.exists(HASH)).isTrue();
  }

  @Test
  void 블랙리스트에_등록되지_않은_해시면_false를_반환한다() {
    when(stringRedisTemplate.hasKey(KEY)).thenReturn(false);

    assertThat(repository.exists(HASH)).isFalse();
  }

  @Test
  void TTL이_0이면_Redis에_저장하지_않는다() {
    repository.add(HASH, Duration.ZERO);

    verify(stringRedisTemplate, org.mockito.Mockito.never()).opsForValue();
  }

  @Test
  void TTL이_음수이면_Redis에_저장하지_않는다() {
    repository.add(HASH, Duration.ofSeconds(-1));

    verify(stringRedisTemplate, org.mockito.Mockito.never()).opsForValue();
  }
}
