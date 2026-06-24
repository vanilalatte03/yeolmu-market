package com.guingujig.yeolmumarket.domain.auth.repository;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisActiveRefreshTokenRepository implements ActiveRefreshTokenRepository {

  private static final String KEY_PREFIX = "auth:refresh:user:";
  private static final RedisScript<Long> ROTATE_SCRIPT =
      RedisScript.of(
          """
          local current = redis.call('GET', KEYS[1])
          if not current then
            return 0
          end
          if current ~= ARGV[1] then
            return 0
          end
          redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
          return 1
          """,
          Long.class);

  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public void save(Long userId, String refreshJti, Duration ttl) {
    stringRedisTemplate.opsForValue().set(key(userId), refreshJti, ttl);
  }

  @Override
  public boolean rotate(Long userId, String currentRefreshJti, String newRefreshJti, Duration ttl) {
    Long result =
        stringRedisTemplate.execute(
            ROTATE_SCRIPT,
            List.of(key(userId)),
            currentRefreshJti,
            newRefreshJti,
            String.valueOf(ttl.toSeconds()));
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public void deleteByUserId(Long userId) {
    stringRedisTemplate.delete(key(userId));
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
