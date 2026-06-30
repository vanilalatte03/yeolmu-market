package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessageResponse;
import com.guingujig.yeolmumarket.domain.chat.service.ChatRoomService;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
  void 전송_요청이_접수되면_구독자에게_즉시_발행한다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);
    ChatMessageResponse response =
        new ChatMessageResponse(
            null,
            "accepted-message-1",
            10L,
            1L,
            "열무구매자",
            "거래 가능할까요?",
            OffsetDateTime.parse("2026-06-22T09:55:00Z"));
    when(chatRoomService.sendMessage(1L, 10L, "거래 가능할까요?")).thenReturn(response);

    controller.sendMessage(10L, "{\"content\":\"거래 가능할까요?\"}", principal);

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    InOrder inOrder = inOrder(messagingTemplate, chatRoomService);
    inOrder
        .verify(messagingTemplate)
        .convertAndSend(eq("/sub/chat-rooms/10"), payloadCaptor.capture());
    inOrder.verify(chatRoomService).saveAcceptedMessageAsync(response);
    assertThat(payloadCaptor.getValue())
        .contains("\"messageId\":null")
        .contains("\"acceptedMessageId\":\"accepted-message-1\"")
        .contains("\"roomId\":10")
        .contains("\"senderId\":1")
        .contains("\"senderNickname\":\"열무구매자\"")
        .contains("\"content\":\"거래 가능할까요?\"")
        .contains("\"createdAt\":\"2026-06-22T09:55:00Z\"");
  }

  @Test
  void 전송_처리_실패시_구독자에게_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);
    when(chatRoomService.sendMessage(1L, 10L, "거래 가능할까요?"))
        .thenThrow(new IllegalStateException("전송 실패"));

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

  @Test
  void 서비스_검증_실패는_user_error_queue로_전송하고_발행하지_않는다() {
    var principal =
        new TestingAuthenticationToken(
            new AuthenticatedUser(1L, "buyer@example.com", UserRole.USER), null);
    when(chatRoomService.sendMessage(1L, 10L, "거래 가능할까요?"))
        .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));

    controller.sendMessage(10L, "{\"content\":\"거래 가능할까요?\"}", principal);

    verify(chatWebSocketErrorSender).sendToUser(principal, ErrorCode.VALIDATION_FAILED, 10L);
    verify(messagingTemplate, never()).convertAndSend(eq("/sub/chat-rooms/10"), anyString());
  }
}
