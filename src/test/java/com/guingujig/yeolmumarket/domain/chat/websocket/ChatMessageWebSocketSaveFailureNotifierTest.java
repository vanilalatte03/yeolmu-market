package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageWebSocketSaveFailureNotifierTest {

  @Mock private ChatWebSocketErrorSender chatWebSocketErrorSender;

  @InjectMocks private ChatMessageWebSocketSaveFailureNotifier notifier;

  @Test
  void 비동기_저장_실패를_방_보정_destination과_user_error_queue로_알린다() {
    notifier.notifyFailure(1L, 10L, "accepted-message-1");

    InOrder inOrder = inOrder(chatWebSocketErrorSender);
    inOrder
        .verify(chatWebSocketErrorSender)
        .sendToUser("1", ErrorCode.CHAT_MESSAGE_SAVE_FAILED, 10L, "accepted-message-1");
    inOrder
        .verify(chatWebSocketErrorSender)
        .sendToChatRoom(10L, ErrorCode.CHAT_MESSAGE_SAVE_FAILED, "accepted-message-1");
  }

  @Test
  void user_error_queue_알림이_실패해도_방_보정_destination_알림을_시도한다() {
    doThrow(new RuntimeException("user send failed"))
        .when(chatWebSocketErrorSender)
        .sendToUser("1", ErrorCode.CHAT_MESSAGE_SAVE_FAILED, 10L, "accepted-message-1");

    assertDoesNotThrow(() -> notifier.notifyFailure(1L, 10L, "accepted-message-1"));

    verify(chatWebSocketErrorSender)
        .sendToChatRoom(10L, ErrorCode.CHAT_MESSAGE_SAVE_FAILED, "accepted-message-1");
  }

  @Test
  void 방_보정_destination_알림이_실패해도_user_error_queue_알림은_호출된다() {
    doThrow(new RuntimeException("room send failed"))
        .when(chatWebSocketErrorSender)
        .sendToChatRoom(10L, ErrorCode.CHAT_MESSAGE_SAVE_FAILED, "accepted-message-1");

    assertDoesNotThrow(() -> notifier.notifyFailure(1L, 10L, "accepted-message-1"));

    verify(chatWebSocketErrorSender)
        .sendToUser("1", ErrorCode.CHAT_MESSAGE_SAVE_FAILED, 10L, "accepted-message-1");
  }
}
