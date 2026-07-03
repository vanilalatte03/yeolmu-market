package com.guingujig.yeolmumarket.domain.chat.entity;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chatmessage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chatroom_id", nullable = false)
  private ChatRoom chatRoom;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "accepted_message_id", length = 36, updatable = false, unique = true)
  private String acceptedMessageId;

  public static ChatMessage create(ChatRoom chatRoom, User sender, String content) {
    return create(
        chatRoom,
        sender,
        content,
        LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
  }

  public static ChatMessage create(
      ChatRoom chatRoom, User sender, String content, LocalDateTime createdAt) {
    return create(chatRoom, sender, content, createdAt, null);
  }

  public static ChatMessage create(
      ChatRoom chatRoom,
      User sender,
      String content,
      LocalDateTime createdAt,
      String acceptedMessageId) {
    ChatMessage chatMessage = new ChatMessage();
    chatMessage.chatRoom = Objects.requireNonNull(chatRoom, "chatRoom은 필수입니다.");
    chatMessage.sender = Objects.requireNonNull(sender, "sender는 필수입니다.");
    chatMessage.content = requireText(content);
    chatMessage.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수입니다.");
    chatMessage.acceptedMessageId = acceptedMessageId;
    return chatMessage;
  }

  private static String requireText(String content) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("메시지 내용은 필수입니다.");
    }
    return content;
  }
}
