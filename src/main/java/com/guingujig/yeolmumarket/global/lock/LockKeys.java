package com.guingujig.yeolmumarket.global.lock;

import java.util.Objects;

public final class LockKeys {

  private static final String ORDER_LOCK_PREFIX = "lock:order:";

  private LockKeys() {}

  public static String order(Long orderId) {
    return ORDER_LOCK_PREFIX + Objects.requireNonNull(orderId, "orderId는 필수입니다.");
  }
}
