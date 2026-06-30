package com.guingujig.yeolmumarket.global.lock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu.lock")
public record LockProperties(Duration waitTime, Duration leaseTime, int txTimeoutSeconds) {

  public LockProperties {
    if (waitTime == null || waitTime.isNegative()) {
      throw new IllegalArgumentException("yeolmu.lock.wait-time은 0 이상이어야 합니다.");
    }
    if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
      throw new IllegalArgumentException("yeolmu.lock.lease-time은 0보다 커야 합니다.");
    }
    if (txTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("yeolmu.lock.tx-timeout-seconds는 0보다 커야 합니다.");
    }
    // 트랜잭션이 lease 만료 전에 끝나도록 강제한다(ADR-013). 위반 시 기동을 막는다.
    if (Duration.ofSeconds(txTimeoutSeconds).compareTo(leaseTime) >= 0) {
      throw new IllegalArgumentException("yeolmu.lock.tx-timeout-seconds는 lease-time보다 작아야 합니다.");
    }
  }
}
