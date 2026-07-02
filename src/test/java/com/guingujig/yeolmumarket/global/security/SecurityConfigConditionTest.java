package com.guingujig.yeolmumarket.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityConfigConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(SecurityConfig.class);

  @Test
  void 웹_컨텍스트가_아니면_보안_설정을_로드하지_않는다() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(SecurityConfig.class);
        });
  }
}
