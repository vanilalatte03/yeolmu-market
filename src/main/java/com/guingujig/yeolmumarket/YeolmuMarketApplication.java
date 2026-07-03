package com.guingujig.yeolmumarket;

import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableConfigurationProperties(YeolmuProperties.class)
@SpringBootApplication
public class YeolmuMarketApplication {

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(YeolmuMarketApplication.class, args);
  }
}
