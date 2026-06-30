package com.guingujig.yeolmumarket.global.lock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu.lock")
public record LockProperties(Duration waitTime, Duration leaseTime) {

  public LockProperties {
    if (waitTime == null || waitTime.isNegative()) {
      throw new IllegalArgumentException("yeolmu.lock.wait-time은 0 이상이어야 합니다.");
    }
    if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
      throw new IllegalArgumentException("yeolmu.lock.lease-time은 0보다 커야 합니다.");
    }
  }
}
