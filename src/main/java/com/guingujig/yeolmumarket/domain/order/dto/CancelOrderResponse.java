package com.guingujig.yeolmumarket.domain.order.dto;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CancelOrderResponse(
    Long orderId, OrderStatus status, ProductStatus productStatus, OffsetDateTime canceledAt) {

  public static CancelOrderResponse from(Order order) {
    return new CancelOrderResponse(
        order.getId(),
        order.getOrderStatus(),
        order.getProduct().getStatus(),
        order.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
