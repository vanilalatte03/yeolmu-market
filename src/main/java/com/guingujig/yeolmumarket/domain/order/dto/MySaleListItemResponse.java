package com.guingujig.yeolmumarket.domain.order.dto;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record MySaleListItemResponse(
    Long orderId,
    Long productId,
    String productTitle,
    Integer price,
    String buyerNickname,
    OrderStatus status,
    OffsetDateTime createdAt) {

  public static MySaleListItemResponse from(Order order) {
    return new MySaleListItemResponse(
        order.getId(),
        order.getProduct().getId(),
        order.getProduct().getTitle(),
        order.getOrderPrice(),
        order.getBuyer().getNickname(),
        order.getOrderStatus(),
        order.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
