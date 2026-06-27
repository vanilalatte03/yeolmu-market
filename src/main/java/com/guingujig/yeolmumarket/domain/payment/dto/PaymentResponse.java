package com.guingujig.yeolmumarket.domain.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
    Long paymentId,
    Long orderId,
    Integer amount,
    PaymentMethod method,
    PaymentStatus status,
    OrderStatus orderStatus,
    ProductStatus productStatus,
    OffsetDateTime paidAt,
    OffsetDateTime failedAt) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getOrder().getId(),
        payment.getAmount(),
        payment.getMethod(),
        payment.getStatus(),
        payment.getOrder().getOrderStatus(),
        payment.getOrder().getProduct().getStatus(),
        payment.getPaidAt() != null ? payment.getPaidAt().atOffset(ZoneOffset.UTC) : null,
        payment.getFailedAt() != null ? payment.getFailedAt().atOffset(ZoneOffset.UTC) : null);
  }
}
