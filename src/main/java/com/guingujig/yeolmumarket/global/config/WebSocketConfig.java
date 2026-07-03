package com.guingujig.yeolmumarket.global.config;

import com.guingujig.yeolmumarket.domain.chat.websocket.ChatStompErrorHandler;
import com.guingujig.yeolmumarket.domain.chat.websocket.ChatSubscriptionAuthorizationInterceptor;
import com.guingujig.yeolmumarket.domain.chat.websocket.ChatWebSocketAuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final ChatWebSocketAuthenticationInterceptor authenticationInterceptor;
  private final ChatSubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor;
  private final ChatStompErrorHandler chatStompErrorHandler;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.setErrorHandler(chatStompErrorHandler);
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/sub", "/queue");
    registry.setApplicationDestinationPrefixes("/pub");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authenticationInterceptor, subscriptionAuthorizationInterceptor);
  }
}
