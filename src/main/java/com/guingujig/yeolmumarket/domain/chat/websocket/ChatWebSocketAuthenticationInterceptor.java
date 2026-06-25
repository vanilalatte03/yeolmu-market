package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.JwtAuthenticationService;
import com.guingujig.yeolmumarket.global.security.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 프레임의 JWT를 검증하고 이후 SUBSCRIBE/SEND에서 재사용할 Principal을 설정한다.
 *
 * <p>HTTP Security 필터 체인은 STOMP frame 내부의 Authorization 헤더를 직접 처리하지 않으므로 inbound channel에서 별도로
 * 인증한다.
 */
@Component
@RequiredArgsConstructor
public class ChatWebSocketAuthenticationInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtAuthenticationService jwtAuthenticationService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = getAccessor(message);
    if (accessor.getCommand() != StompCommand.CONNECT) {
      return message;
    }

    String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      throw new ChatWebSocketAuthenticationException(ErrorCode.UNAUTHORIZED);
    }

    try {
      Authentication authentication =
          jwtAuthenticationService.authenticate(authorization.substring(BEARER_PREFIX.length()));
      accessor.setUser(authentication);
      return message;
    } catch (JwtException exception) {
      throw new ChatWebSocketAuthenticationException(exception.getErrorCode());
    } catch (BusinessException exception) {
      throw new ChatWebSocketAuthenticationException(exception.getErrorCode());
    } catch (DataAccessException exception) {
      throw new ChatWebSocketAuthenticationException(ErrorCode.REDIS_UNAVAILABLE);
    }
  }

  private StompHeaderAccessor getAccessor(Message<?> message) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    return accessor != null ? accessor : StompHeaderAccessor.wrap(message);
  }
}
