package com.guingujig.yeolmumarket.domain.payment.dto;

import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record PaymentDetailResponse(
    Long paymentId,
    Long orderId,
    Integer amount,
    PaymentMethod method,
    PaymentStatus status,
    OffsetDateTime paidAt,
    OffsetDateTime canceledAt) {

  public static PaymentDetailResponse from(Payment payment) {
    return new PaymentDetailResponse(
        payment.getId(),
        payment.getOrder().getId(),
        payment.getAmount(),
        payment.getMethod(),
        payment.getStatus(),
        payment.getPaidAt() != null ? payment.getPaidAt().atOffset(ZoneOffset.UTC) : null,
        payment.getCanceledAt() != null ? payment.getCanceledAt().atOffset(ZoneOffset.UTC) : null);
  }
}
