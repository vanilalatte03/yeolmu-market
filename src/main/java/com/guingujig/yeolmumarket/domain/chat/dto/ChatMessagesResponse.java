package com.guingujig.yeolmumarket.domain.chat.dto;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import java.util.List;

public record ChatMessagesResponse(List<ChatMessageResponse> messages, boolean hasNext) {

  public static ChatMessagesResponse of(List<ChatMessage> messages, boolean hasNext) {
    return new ChatMessagesResponse(
        messages.stream().map(ChatMessageResponse::from).toList(), hasNext);
  }
}
