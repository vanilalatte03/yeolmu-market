package com.guingujig.yeolmumarket.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yeolmu.async.chat-message")
public record ChatMessageAsyncProperties(
    Integer corePoolSize,
    Integer maxPoolSize,
    Integer queueCapacity,
    Integer awaitTerminationSeconds) {

  private static final int DEFAULT_CORE_POOL_SIZE = 2;
  private static final int DEFAULT_MAX_POOL_SIZE = 8;
  private static final int DEFAULT_QUEUE_CAPACITY = 1000;
  private static final int DEFAULT_AWAIT_TERMINATION_SECONDS = 10;

  public ChatMessageAsyncProperties {
    corePoolSize = positiveOrDefault(corePoolSize, DEFAULT_CORE_POOL_SIZE);
    maxPoolSize = positiveOrDefault(maxPoolSize, DEFAULT_MAX_POOL_SIZE);
    if (maxPoolSize < corePoolSize) {
      maxPoolSize = corePoolSize;
    }
    queueCapacity = positiveOrDefault(queueCapacity, DEFAULT_QUEUE_CAPACITY);
    awaitTerminationSeconds =
        positiveOrDefault(awaitTerminationSeconds, DEFAULT_AWAIT_TERMINATION_SECONDS);
  }

  private static int positiveOrDefault(Integer value, int defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }
}
