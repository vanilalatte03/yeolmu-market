package com.guingujig.yeolmumarket.domain.order.dto;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ConfirmOrderResponse(
    Long orderId, OrderStatus status, ProductStatus productStatus, OffsetDateTime confirmedAt) {

  public static ConfirmOrderResponse from(Order order) {
    return new ConfirmOrderResponse(
        order.getId(),
        order.getOrderStatus(),
        order.getProduct().getStatus(),
        order.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
