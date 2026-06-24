package com.guingujig.yeolmumarket.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisActiveRefreshTokenRepositoryTest {

  private static final String KEY = "auth:refresh:user:1";

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisActiveRefreshTokenRepository repository;

  @BeforeEach
  void setUp() {
    repository = new RedisActiveRefreshTokenRepository(stringRedisTemplate);
  }

  @Test
  void 활성_refresh_token_해시를_TTL과_함께_저장한다() {
    Duration ttl = Duration.ofSeconds(1209600);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    repository.save(1L, "token-hash", ttl);

    verify(valueOperations).set(KEY, "token-hash", ttl);
  }

  @Test
  void 사용자별_활성_refresh_token_해시를_조회한다() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn("token-hash");

    Optional<String> tokenHash = repository.findHashByUserId(1L);

    assertThat(tokenHash).contains("token-hash");
  }

  @Test
  void 사용자별_활성_refresh_token_해시가_없으면_빈_값을_반환한다() {
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn(null);

    Optional<String> tokenHash = repository.findHashByUserId(1L);

    assertThat(tokenHash).isEmpty();
  }

  @Test
  void 사용자별_활성_refresh_token_해시를_삭제한다() {
    repository.deleteByUserId(1L);

    verify(stringRedisTemplate).delete(KEY);
  }
}
