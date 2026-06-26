package com.guingujig.yeolmumarket.domain.order.dto;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record MyOrderListItemResponse(
    Long orderId,
    Long productId,
    String productTitle,
    Integer price,
    String sellerNickname,
    OrderStatus status,
    OffsetDateTime createdAt) {

  public static MyOrderListItemResponse from(Order order) {
    return new MyOrderListItemResponse(
        order.getId(),
        order.getProduct().getId(),
        order.getProduct().getTitle(),
        order.getOrderPrice(),
        order.getSeller().getNickname(),
        order.getOrderStatus(),
        order.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
