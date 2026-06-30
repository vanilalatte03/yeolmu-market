package com.guingujig.yeolmumarket.global.lock;

import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "yeolmu.lock.redisson.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RedissonDistributedLockExecutor implements DistributedLockExecutor {

  private static final Logger log = LoggerFactory.getLogger(RedissonDistributedLockExecutor.class);

  private final RedissonClient redissonClient;
  private final LockProperties properties;

  @Override
  public <T> T execute(String key, LockCallback<T> callback) {
    RLock lock = redissonClient.getLock(key);
    boolean locked = acquire(lock, key);
    if (!locked) {
      throw new BusinessException(ErrorCode.CONFLICT);
    }

    try {
      return callback.execute();
    } finally {
      unlock(lock, key);
    }
  }

  private boolean acquire(RLock lock, String key) {
    try {
      return lock.tryLock(
          properties.waitTime().toMillis(),
          properties.leaseTime().toMillis(),
          TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.CONFLICT);
    } catch (RedisConnectionException exception) {
      throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    } catch (RedisException exception) {
      throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }
  }

  private void unlock(RLock lock, String key) {
    try {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    } catch (RedisException exception) {
      log.warn("분산락 해제에 실패했습니다. key={}", key, exception);
    } catch (IllegalMonitorStateException exception) {
      log.warn("현재 스레드가 보유하지 않은 분산락 해제를 시도했습니다. key={}", key, exception);
    }
  }
}
