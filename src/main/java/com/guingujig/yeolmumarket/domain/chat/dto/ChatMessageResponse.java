package com.guingujig.yeolmumarket.domain.chat.dto;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ChatMessageResponse(
    Long messageId,
    Long roomId,
    Long senderId,
    String senderNickname,
    String content,
    OffsetDateTime createdAt) {

  public static ChatMessageResponse from(ChatMessage message) {
    return new ChatMessageResponse(
        message.getId(),
        message.getChatRoom().getId(),
        message.getSender().getId(),
        message.getSender().getNickname(),
        message.getContent(),
        message.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
