package com.guingujig.yeolmumarket.domain.chat.dto;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ChatRoomListItemResponse(
    Long roomId,
    Long productId,
    String productTitle,
    String opponentNickname,
    String lastMessage,
    OffsetDateTime lastMessageAt) {

  public static ChatRoomListItemResponse of(
      ChatRoom chatRoom, Long currentUserId, ChatMessage lastMessage) {
    return new ChatRoomListItemResponse(
        chatRoom.getId(),
        chatRoom.getProduct().getId(),
        chatRoom.getProduct().getTitle(),
        resolveOpponentNickname(chatRoom, currentUserId),
        resolveLastMessageContent(lastMessage),
        resolveLastMessageAt(lastMessage));
  }

  private static String resolveOpponentNickname(ChatRoom chatRoom, Long currentUserId) {
    if (chatRoom.getBuyer().getId().equals(currentUserId)) {
      return chatRoom.getSeller().getNickname();
    }
    return chatRoom.getBuyer().getNickname();
  }

  private static String resolveLastMessageContent(ChatMessage lastMessage) {
    if (lastMessage == null) {
      return null;
    }
    return lastMessage.getContent();
  }

  private static OffsetDateTime resolveLastMessageAt(ChatMessage lastMessage) {
    if (lastMessage == null) {
      return null;
    }
    return lastMessage.getCreatedAt().atOffset(ZoneOffset.UTC);
  }
}
