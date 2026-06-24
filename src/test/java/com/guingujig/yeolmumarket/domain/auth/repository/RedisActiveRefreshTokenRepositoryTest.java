package com.guingujig.yeolmumarket.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

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
  void 활성_refresh_token_jti를_TTL과_함께_저장한다() {
    Duration ttl = Duration.ofSeconds(1209600);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

    repository.save(1L, "refresh-jti", ttl);

    verify(valueOperations).set(KEY, "refresh-jti", ttl);
  }

  @Test
  void 현재_jti가_일치하면_lua_script로_새_jti로_교체한다() {
    Duration ttl = Duration.ofSeconds(1209600);
    when(stringRedisTemplate.execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of(KEY)),
            eq("old-jti"),
            eq("new-jti"),
            eq("1209600")))
        .thenReturn(1L);

    boolean rotated = repository.rotate(1L, "old-jti", "new-jti", ttl);

    assertThat(rotated).isTrue();
    verify(stringRedisTemplate)
        .execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of(KEY)),
            eq("old-jti"),
            eq("new-jti"),
            eq("1209600"));
  }

  @Test
  void 현재_jti가_일치하지_않으면_교체하지_않는다() {
    Duration ttl = Duration.ofSeconds(1209600);
    when(stringRedisTemplate.execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of(KEY)),
            eq("old-jti"),
            eq("new-jti"),
            eq("1209600")))
        .thenReturn(0L);

    boolean rotated = repository.rotate(1L, "old-jti", "new-jti", ttl);

    assertThat(rotated).isFalse();
  }

  @Test
  void 사용자별_활성_refresh_token_해시를_삭제한다() {
    repository.deleteByUserId(1L);

    verify(stringRedisTemplate).delete(KEY);
  }
}
