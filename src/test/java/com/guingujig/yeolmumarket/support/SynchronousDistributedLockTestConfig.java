package com.guingujig.yeolmumarket.support;

import com.guingujig.yeolmumarket.global.lock.DistributedLockExecutor;
import com.guingujig.yeolmumarket.global.lock.LockCallback;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "yeolmu.lock.redisson.enabled", havingValue = "false")
public class SynchronousDistributedLockTestConfig {

  @Bean
  @Primary
  public DistributedLockExecutor distributedLockExecutor() {
    ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    return new DistributedLockExecutor() {
      @Override
      public <T> T execute(String key, LockCallback<T> callback) {
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
          return callback.execute();
        } finally {
          lock.unlock();
        }
      }
    };
  }
}
