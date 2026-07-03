package com.guingujig.yeolmumarket.global.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;

@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockExecutorTest {

  private static final String LOCK_KEY = "lock:order:1";

  @Mock private RedissonClient redissonClient;
  @Mock private RLock lock;

  @Test
  void 락을_획득하면_콜백을_실행하고_락을_해제한다() throws InterruptedException {
    RedissonDistributedLockExecutor executor = executor();
    when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
    when(lock.tryLock(10, 2000, TimeUnit.MILLISECONDS)).thenReturn(true);
    when(lock.isHeldByCurrentThread()).thenReturn(true);

    String result = executor.execute(LOCK_KEY, () -> "success");

    assertThat(result).isEqualTo("success");
    verify(lock).unlock();
  }

  @Test
  void 락_획득에_실패하면_CONFLICT로_거절하고_콜백을_실행하지_않는다() throws InterruptedException {
    RedissonDistributedLockExecutor executor = executor();
    AtomicBoolean callbackCalled = new AtomicBoolean(false);
    when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
    when(lock.tryLock(10, 2000, TimeUnit.MILLISECONDS)).thenReturn(false);

    assertThatThrownBy(
            () ->
                executor.execute(
                    LOCK_KEY,
                    () -> {
                      callbackCalled.set(true);
                      return "ignored";
                    }))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    assertThat(callbackCalled).isFalse();
    verify(lock, never()).unlock();
  }

  @Test
  void Redis_연결_장애가_발생하면_REDIS_UNAVAILABLE로_변환한다() throws InterruptedException {
    RedissonDistributedLockExecutor executor = executor();
    when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
    when(lock.tryLock(10, 2000, TimeUnit.MILLISECONDS))
        .thenThrow(new RedisConnectionException("redis unavailable"));

    assertThatThrownBy(() -> executor.execute(LOCK_KEY, () -> "ignored"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE));
  }

  private RedissonDistributedLockExecutor executor() {
    return new RedissonDistributedLockExecutor(
        redissonClient, new LockProperties(Duration.ofMillis(10), Duration.ofSeconds(2), 1));
  }
}
