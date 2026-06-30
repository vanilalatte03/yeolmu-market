package com.guingujig.yeolmumarket.global.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Redisson Spring Boot starter가 {@code spring.data.redis.*} 설정으로 {@link RedissonClient} 빈을 실제로
 * 만들어내는지 검증하는 wiring smoke test다(ADR-013).
 *
 * <p>기본 테스트 프로파일은 RedissonAutoConfiguration을 제외하고 수동 {@code RedissonClient}로 락 로직만 검증한다. 이 테스트는 그
 * 제외를 비워 starter 자동설정을 켜고, 운영과 동일한 경로로 빈이 생성·연결되는지 확인한다. 실 Redis가 필요하므로 {@code
 * YEOLMU_REDIS_LOCK_TEST=true}와 도달 가능한 Redis(CI service container)를 전제로 하며, 환경변수가 없으면 자동으로 건너뛴다.
 */
@SpringBootTest(properties = {"spring.autoconfigure.exclude=", "yeolmu.lock.redisson.enabled=true"})
@EnabledIfEnvironmentVariable(named = "YEOLMU_REDIS_LOCK_TEST", matches = "true")
class RedissonAutoConfigurationWiringTest {

  @Autowired private RedissonClient redissonClient;

  @Test
  void starter가_RedissonClient_빈을_생성하고_락을_획득할_수_있다() {
    assertThat(redissonClient).isNotNull();

    RLock lock = redissonClient.getLock("lock:wiring-smoke-test");
    assertThat(lock.tryLock()).isTrue();
    lock.unlock();
  }
}
