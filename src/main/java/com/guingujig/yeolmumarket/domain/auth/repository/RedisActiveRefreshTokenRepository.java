package com.guingujig.yeolmumarket.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisActiveRefreshTokenRepository implements ActiveRefreshTokenRepository {

  private static final String KEY_PREFIX = "auth:refresh:user:";

  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public void save(Long userId, String tokenHash, Duration ttl) {
    stringRedisTemplate.opsForValue().set(key(userId), tokenHash, ttl);
  }

  @Override
  public Optional<String> findHashByUserId(Long userId) {
    return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key(userId)));
  }

  @Override
  public void deleteByUserId(Long userId) {
    stringRedisTemplate.delete(key(userId));
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
