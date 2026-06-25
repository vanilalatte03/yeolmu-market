package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * STOMP CONNECT 단계의 인증 실패를 JSON ERROR frame으로 변환한다.
 *
 * <p>CONNECT가 실패하면 아직 사용자 목적지(`/user/queue/errors`)를 안정적으로 사용할 수 없으므로, 이 핸들러가 직접 ERROR frame을 만든다.
 */
@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

  private final ObjectMapper objectMapper;

  @Override
  public Message<byte[]> handleClientMessageProcessingError(
      Message<byte[]> clientMessage, Throwable exception) {
    ChatWebSocketAuthenticationException authenticationException =
        findAuthenticationException(exception);
    if (authenticationException == null) {
      return super.handleClientMessageProcessingError(clientMessage, exception);
    }

    ErrorCode errorCode = authenticationException.getErrorCode();
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
    accessor.setMessage(errorCode.getMessage());
    accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(
        serialize(ChatWebSocketErrorResponse.of(errorCode)), accessor.getMessageHeaders());
  }

  private ChatWebSocketAuthenticationException findAuthenticationException(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof ChatWebSocketAuthenticationException authenticationException) {
        return authenticationException;
      }
      current = current.getCause();
    }
    return null;
  }

  private byte[] serialize(ChatWebSocketErrorResponse response) {
    try {
      return objectMapper.writeValueAsBytes(response);
    } catch (JacksonException exception) {
      throw new IllegalStateException("STOMP ERROR 응답 직렬화에 실패했습니다.", exception);
    }
  }
}
