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
 * CONNECT 이후 작업 단위 오류를 사용자 큐 또는 채팅방 보정 destination으로 전달한다.
 *
 * <p>서버는 `/queue/errors`로 보내고, 클라이언트는 user destination prefix가 붙은 `/user/queue/errors`를 구독한다.
 */
@Component
@RequiredArgsConstructor
public class ChatWebSocketErrorSender {

  private static final String USER_ERROR_DESTINATION = "/queue/errors";
  private static final String CHAT_ROOM_ERROR_DESTINATION_PREFIX = "/sub/chat-rooms/";
  private static final String CHAT_ROOM_ERROR_DESTINATION_SUFFIX = "/errors";

  private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
  private final ObjectMapper objectMapper;

  public void sendToUser(Principal user, ErrorCode errorCode, Long roomId) {
    sendToUser(user.getName(), errorCode, roomId);
  }

  public void sendToUser(String userName, ErrorCode errorCode, Long roomId) {
    sendToUser(userName, errorCode, roomId, null);
  }

  public void sendToUser(
      String userName, ErrorCode errorCode, Long roomId, String acceptedMessageId) {
    messagingTemplateProvider
        .getObject()
        .convertAndSendToUser(
            userName,
            USER_ERROR_DESTINATION,
            serialize(ChatWebSocketErrorResponse.of(errorCode, roomId, acceptedMessageId)));
  }

  public void sendToChatRoom(Long roomId, ErrorCode errorCode, String acceptedMessageId) {
    messagingTemplateProvider
        .getObject()
        .convertAndSend(
            CHAT_ROOM_ERROR_DESTINATION_PREFIX + roomId + CHAT_ROOM_ERROR_DESTINATION_SUFFIX,
            serialize(ChatWebSocketErrorResponse.of(errorCode, roomId, acceptedMessageId)));
  }

  private String serialize(ChatWebSocketErrorResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JacksonException exception) {
      throw new IllegalStateException("WebSocket 오류 응답 직렬화에 실패했습니다.", exception);
    }
  }
}
