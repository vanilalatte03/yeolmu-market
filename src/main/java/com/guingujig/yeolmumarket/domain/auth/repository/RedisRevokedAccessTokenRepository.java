package com.guingujig.yeolmumarket.domain.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisRevokedAccessTokenRepository implements RevokedAccessTokenRepository {

  private static final String KEY_PREFIX = "auth:revoked:access:";
  private static final String MARKER = "1";

  private final StringRedisTemplate stringRedisTemplate;

  @Override
  public void add(String tokenHash, Duration ttl) {
    stringRedisTemplate.opsForValue().set(key(tokenHash), MARKER, ttl);
  }

  @Override
  public boolean exists(String tokenHash) {
    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(tokenHash)));
  }

  private String key(String tokenHash) {
    return KEY_PREFIX + tokenHash;
  }
}
