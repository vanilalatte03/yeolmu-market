package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessageResponse;
import com.guingujig.yeolmumarket.domain.chat.dto.SendChatMessageRequest;
import com.guingujig.yeolmumarket.domain.chat.service.ChatRoomService;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Controller
@RequiredArgsConstructor
public class ChatMessageWebSocketController {

  private static final String CHAT_ROOM_DESTINATION_PREFIX = "/sub/chat-rooms/";

  private final ChatRoomService chatRoomService;
  private final ChatWebSocketErrorSender chatWebSocketErrorSender;
  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  @MessageMapping("/chat-rooms/{roomId}/message")
  public void sendMessage(
      @DestinationVariable Long roomId, Message<byte[]> message, Principal principal) {
    AuthenticatedUser sender = authenticatedUser(principal);
    SendChatMessageRequest request = parseRequest(message, principal, roomId);
    if (request == null || hasValidationError(request, principal, roomId)) {
      return;
    }

    try {
      ChatMessageResponse response =
          chatRoomService.sendMessage(sender.userId(), roomId, request.content());
      messagingTemplate.convertAndSend(CHAT_ROOM_DESTINATION_PREFIX + roomId, serialize(response));
      chatRoomService.saveAcceptedMessageAsync(response);
    } catch (BusinessException exception) {
      if (isSendBusinessError(exception.getErrorCode())) {
        chatWebSocketErrorSender.sendToUser(principal, exception.getErrorCode(), roomId);
        return;
      }
      throw exception;
    }
  }

  private SendChatMessageRequest parseRequest(
      Message<byte[]> message, Principal principal, Long roomId) {
    if (message == null || message.getPayload() == null || message.getPayload().length == 0) {
      chatWebSocketErrorSender.sendToUser(principal, ErrorCode.VALIDATION_FAILED, roomId);
      return null;
    }
    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
    if (payload.isBlank()) {
      chatWebSocketErrorSender.sendToUser(principal, ErrorCode.VALIDATION_FAILED, roomId);
      return null;
    }
    try {
      SendChatMessageRequest request =
          objectMapper.readValue(payload, SendChatMessageRequest.class);
      if (request == null) {
        chatWebSocketErrorSender.sendToUser(principal, ErrorCode.VALIDATION_FAILED, roomId);
      }
      return request;
    } catch (JacksonException exception) {
      chatWebSocketErrorSender.sendToUser(principal, ErrorCode.VALIDATION_FAILED, roomId);
      return null;
    }
  }

  private String serialize(ChatMessageResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JacksonException exception) {
      throw new IllegalStateException("WebSocket 메시지 응답 직렬화에 실패했습니다.", exception);
    }
  }

  private boolean hasValidationError(
      SendChatMessageRequest request, Principal principal, Long roomId) {
    if (validator.validate(request).isEmpty()) {
      return false;
    }
    chatWebSocketErrorSender.sendToUser(principal, ErrorCode.VALIDATION_FAILED, roomId);
    return true;
  }

  private AuthenticatedUser authenticatedUser(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
      return authenticatedUser;
    }
    throw new ChatWebSocketAuthenticationException(ErrorCode.UNAUTHORIZED);
  }

  private boolean isSendBusinessError(ErrorCode errorCode) {
    return errorCode == ErrorCode.CHAT_ROOM_NOT_FOUND
        || errorCode == ErrorCode.CHAT_ROOM_ACCESS_DENIED
        || errorCode == ErrorCode.VALIDATION_FAILED;
  }
}
