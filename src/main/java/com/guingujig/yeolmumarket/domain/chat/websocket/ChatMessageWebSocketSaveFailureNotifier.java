package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.domain.chat.service.ChatMessageSaveFailureNotifier;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageWebSocketSaveFailureNotifier implements ChatMessageSaveFailureNotifier {

  private final ChatWebSocketErrorSender chatWebSocketErrorSender;

  @Override
  public void notifyFailure(Long senderId, Long roomId, String acceptedMessageId) {
    notifySender(senderId, roomId, acceptedMessageId);
    notifyChatRoom(roomId, acceptedMessageId);
  }

  private void notifySender(Long senderId, Long roomId, String acceptedMessageId) {
    try {
      chatWebSocketErrorSender.sendToUser(
          senderId.toString(), ErrorCode.CHAT_MESSAGE_SAVE_FAILED, roomId, acceptedMessageId);
    } catch (RuntimeException exception) {
      log.warn(
          "채팅 메시지 저장 실패 발신자 알림 전송에 실패했습니다. roomId={}, senderId={}, acceptedMessageId={}",
          roomId,
          senderId,
          acceptedMessageId,
          exception);
    }
  }

  private void notifyChatRoom(Long roomId, String acceptedMessageId) {
    try {
      chatWebSocketErrorSender.sendToChatRoom(
          roomId, ErrorCode.CHAT_MESSAGE_SAVE_FAILED, acceptedMessageId);
    } catch (RuntimeException exception) {
      log.warn(
          "채팅 메시지 저장 실패 방 보정 알림 전송에 실패했습니다. roomId={}, acceptedMessageId={}",
          roomId,
          acceptedMessageId,
          exception);
    }
  }
}
