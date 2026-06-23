package com.guingujig.yeolmumarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class YeolmuMarketApplication {

  public static void main(String[] args) {
    SpringApplication.run(YeolmuMarketApplication.class, args);
  }
}
