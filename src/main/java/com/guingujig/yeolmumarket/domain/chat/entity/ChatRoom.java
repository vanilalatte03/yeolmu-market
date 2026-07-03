package com.guingujig.yeolmumarket.domain.chat.entity;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(
    name = "chatroom",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_chatroom_product_buyer_seller",
            columnNames = {"product_id", "buyer_id", "seller_id"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private User seller;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "buyer_id", nullable = false)
  private User buyer;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_message_at")
  private LocalDateTime lastMessageAt;

  public static ChatRoom create(Product product, User buyer) {
    ChatRoom chatRoom = new ChatRoom();
    chatRoom.product = Objects.requireNonNull(product, "product는 필수입니다.");
    chatRoom.buyer = Objects.requireNonNull(buyer, "buyer는 필수입니다.");
    chatRoom.seller = Objects.requireNonNull(product.getSeller(), "seller는 필수입니다.");
    return chatRoom;
  }

  public boolean isParticipant(Long userId) {
    return buyer.getId().equals(userId) || seller.getId().equals(userId);
  }

  public User getParticipant(Long userId) {
    if (buyer.getId().equals(userId)) {
      return buyer;
    }
    if (seller.getId().equals(userId)) {
      return seller;
    }
    throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
  }
}
