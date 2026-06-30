package com.guingujig.yeolmumarket.domain.chat.service;

public interface ChatMessageSaveFailureNotifier {

  void notifyFailure(Long senderId, Long roomId, String acceptedMessageId);
}
