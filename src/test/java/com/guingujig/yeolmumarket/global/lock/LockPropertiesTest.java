package com.guingujig.yeolmumarket.global.lock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LockPropertiesTest {

  @Test
  void txTimeout이_lease보다_작으면_생성된다() {
    assertThatCode(() -> new LockProperties(Duration.ofMillis(500), Duration.ofSeconds(10), 8))
        .doesNotThrowAnyException();
  }

  @Test
  void txTimeout이_lease와_같으면_거부한다() {
    assertThatThrownBy(() -> new LockProperties(Duration.ofMillis(500), Duration.ofSeconds(8), 8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lease-time보다 작아야");
  }

  @Test
  void txTimeout이_lease보다_크면_거부한다() {
    assertThatThrownBy(() -> new LockProperties(Duration.ofMillis(500), Duration.ofSeconds(5), 8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lease-time보다 작아야");
  }

  @Test
  void txTimeout이_0이하면_거부한다() {
    assertThatThrownBy(() -> new LockProperties(Duration.ofMillis(500), Duration.ofSeconds(10), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
