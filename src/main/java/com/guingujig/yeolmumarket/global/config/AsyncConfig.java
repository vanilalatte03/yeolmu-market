package com.guingujig.yeolmumarket.global.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ChatMessageAsyncProperties.class)
public class AsyncConfig {

  @Bean(name = "chatMessageTaskExecutor")
  public Executor chatMessageTaskExecutor(ChatMessageAsyncProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.corePoolSize());
    executor.setMaxPoolSize(properties.maxPoolSize());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("chat-message-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(properties.awaitTerminationSeconds());
    executor.initialize();
    return executor;
  }
}
