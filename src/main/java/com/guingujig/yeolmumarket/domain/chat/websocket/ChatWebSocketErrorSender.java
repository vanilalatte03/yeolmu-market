package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * CONNECT 이후 작업 단위 오류를 현재 사용자에게만 전달한다.
 *
 * <p>서버는 `/queue/errors`로 보내고, 클라이언트는 user destination prefix가 붙은 `/user/queue/errors`를 구독한다.
 */
@Component
@RequiredArgsConstructor
public class ChatWebSocketErrorSender {

  private static final String USER_ERROR_DESTINATION = "/queue/errors";

  private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
  private final ObjectMapper objectMapper;

  public void sendToUser(Principal user, ErrorCode errorCode, Long roomId) {
    messagingTemplateProvider
        .getObject()
        .convertAndSendToUser(
            user.getName(),
            USER_ERROR_DESTINATION,
            serialize(ChatWebSocketErrorResponse.of(errorCode, roomId)));
  }

  private String serialize(ChatWebSocketErrorResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JacksonException exception) {
      throw new IllegalStateException("WebSocket 오류 응답 직렬화에 실패했습니다.", exception);
    }
  }
}
