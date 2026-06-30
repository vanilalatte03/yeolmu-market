package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.domain.chat.service.ChatMessageSaveFailureNotifier;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageWebSocketSaveFailureNotifier implements ChatMessageSaveFailureNotifier {

  private final ChatWebSocketErrorSender chatWebSocketErrorSender;

  @Override
  public void notifyFailure(Long senderId, Long roomId, String acceptedMessageId) {
    chatWebSocketErrorSender.sendToChatRoom(
        roomId, ErrorCode.CHAT_MESSAGE_SAVE_FAILED, acceptedMessageId);
    chatWebSocketErrorSender.sendToUser(
        senderId.toString(), ErrorCode.CHAT_MESSAGE_SAVE_FAILED, roomId, acceptedMessageId);
  }
}
