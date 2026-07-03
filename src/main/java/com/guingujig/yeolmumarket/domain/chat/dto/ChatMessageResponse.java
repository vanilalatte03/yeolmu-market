package com.guingujig.yeolmumarket.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ChatMessageResponse(
    Long messageId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String acceptedMessageId,
    Long roomId,
    Long senderId,
    String senderNickname,
    String content,
    OffsetDateTime createdAt) {

  public static ChatMessageResponse from(ChatMessage message) {
    return new ChatMessageResponse(
        message.getId(),
        message.getAcceptedMessageId(),
        message.getChatRoom().getId(),
        message.getSender().getId(),
        message.getSender().getNickname(),
        message.getContent(),
        message.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  public static ChatMessageResponse accepted(
      String acceptedMessageId,
      Long roomId,
      User sender,
      String content,
      LocalDateTime acceptedAt) {
    return new ChatMessageResponse(
        null,
        acceptedMessageId,
        roomId,
        sender.getId(),
        sender.getNickname(),
        content,
        acceptedAt.atOffset(ZoneOffset.UTC));
  }
}
