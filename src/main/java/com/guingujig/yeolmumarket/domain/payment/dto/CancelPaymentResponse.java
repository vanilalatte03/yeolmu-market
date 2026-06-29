package com.guingujig.yeolmumarket.domain.payment.dto;

import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;

public record CancelPaymentResponse(
    Long paymentId,
    Long orderId,
    PaymentStatus status,
    OrderStatus orderStatus,
    ProductStatus productStatus,
    OffsetDateTime canceledAt) {

  public static CancelPaymentResponse from(Payment payment) {
    return new CancelPaymentResponse(
        payment.getId(),
        payment.getOrder().getId(),
        payment.getStatus(),
        payment.getOrder().getOrderStatus(),
        payment.getOrder().getProduct().getStatus(),
        PaymentDateTimes.toUtcOffset(payment.getCanceledAt()));
  }
}
