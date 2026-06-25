package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.chat.service.ChatRoomService;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChatMessageWebSocketControllerTest {

  @Mock private ChatRoomService chatRoomService;
  @Mock private ChatWebSocketErrorSender chatWebSocketErrorSender;
  @Mock private SimpMessagingTemplate messagingTemplate;

  private ChatMessageWebSocketController controller;

  @BeforeEach
  void setUp() {
    controller =
        new ChatMessageWebSocketController(
            chatRoomService,
            chatWebSocketErrorSender,
            messagingTemplate,
            new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator());
  }

  @Test
  void 메시지_저장_실패시_구독자에게_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);
    when(chatRoomService.sendMessage(1L, 10L, "거래 가능할까요?"))
        .thenThrow(new IllegalStateException("저장 실패"));

    assertThatThrownBy(() -> controller.sendMessage(10L, "{\"content\":\"거래 가능할까요?\"}", principal))
        .isInstanceOf(IllegalStateException.class);

    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }

  @Test
  void null_payload는_validation_failed를_전송하고_저장_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);

    controller.sendMessage(10L, null, principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.VALIDATION_FAILED, 10L);
    verifyNoInteractions(chatRoomService);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }

  @Test
  void json_null_payload는_validation_failed를_전송하고_저장_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);

    controller.sendMessage(10L, "null", principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.VALIDATION_FAILED, 10L);
    verifyNoInteractions(chatRoomService);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }

  @Test
  void raw_blank_payload는_validation_failed를_전송하고_저장_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);

    controller.sendMessage(10L, "   ", principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.VALIDATION_FAILED, 10L);
    verifyNoInteractions(chatRoomService);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }

  @Test
  void 공백_메시지는_validation_failed를_전송하고_저장_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);

    controller.sendMessage(10L, "{\"content\":\"   \"}", principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.VALIDATION_FAILED, 10L);
    verifyNoInteractions(chatRoomService);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }

  @Test
  void 잘못된_JSON은_validation_failed를_전송하고_저장_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);

    controller.sendMessage(10L, "{", principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.VALIDATION_FAILED, 10L);
    verifyNoInteractions(chatRoomService);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }

  @Test
  void 참여자_아님_오류는_user_error_queue로_전송하고_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);
    when(chatRoomService.sendMessage(1L, 10L, "거래 가능할까요?"))
        .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

    controller.sendMessage(10L, "{\"content\":\"거래 가능할까요?\"}", principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.CHAT_ROOM_ACCESS_DENIED, 10L);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }
}
