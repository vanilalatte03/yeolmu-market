package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.mockito.Mockito.verify;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    verify(chatWebSocketErrorSender)
        .sendToChatRoom(10L, ErrorCode.CHAT_MESSAGE_SAVE_FAILED, "accepted-message-1");
    verify(chatWebSocketErrorSender)
        .sendToUser("1", ErrorCode.CHAT_MESSAGE_SAVE_FAILED, 10L, "accepted-message-1");
  }
}
