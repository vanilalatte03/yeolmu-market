package com.guingujig.yeolmumarket.domain.order.dto;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record RegisterOrderShippingResponse(
    Long orderId, OrderStatus status, String trackingNumber, OffsetDateTime shippedAt) {

  public static RegisterOrderShippingResponse from(Order order) {
    return new RegisterOrderShippingResponse(
        order.getId(),
        order.getOrderStatus(),
        order.getTrackingNumber(),
        order.getShippedAt().atOffset(ZoneOffset.UTC));
  }
}
