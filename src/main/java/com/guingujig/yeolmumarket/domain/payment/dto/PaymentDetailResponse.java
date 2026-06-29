package com.guingujig.yeolmumarket.domain.payment.dto;

import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import java.time.OffsetDateTime;

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
        PaymentDateTimes.toUtcOffset(payment.getPaidAt()),
        PaymentDateTimes.toUtcOffset(payment.getCanceledAt()));
  }
}
