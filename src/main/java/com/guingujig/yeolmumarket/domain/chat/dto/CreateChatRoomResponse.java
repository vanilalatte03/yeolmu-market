package com.guingujig.yeolmumarket.domain.chat.dto;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CreateChatRoomResponse(
    Long roomId,
    ProductSummary product,
    UserSummary buyer,
    UserSummary seller,
    OffsetDateTime createdAt) {

  public static CreateChatRoomResponse from(ChatRoom chatRoom) {
    return new CreateChatRoomResponse(
        chatRoom.getId(),
        ProductSummary.from(chatRoom.getProduct()),
        UserSummary.from(chatRoom.getBuyer()),
        UserSummary.from(chatRoom.getSeller()),
        chatRoom.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  public record ProductSummary(Long productId, String title) {
    public static ProductSummary from(Product product) {
      return new ProductSummary(product.getId(), product.getTitle());
    }
  }

  public record UserSummary(Long userId, String nickname) {
    public static UserSummary from(User user) {
      return new UserSummary(user.getId(), user.getNickname());
    }
  }
}
