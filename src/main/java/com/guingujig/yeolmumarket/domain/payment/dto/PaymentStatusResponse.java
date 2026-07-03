package com.guingujig.yeolmumarket.domain.payment.dto;

import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import java.time.OffsetDateTime;

public record PaymentStatusResponse(
    Long paymentId, Long orderId, PaymentStatus status, Integer amount, OffsetDateTime paidAt) {

  public static PaymentStatusResponse from(Payment payment) {
    return new PaymentStatusResponse(
        payment.getId(),
        payment.getOrder().getId(),
        payment.getStatus(),
        payment.getAmount(),
        PaymentDateTimes.toUtcOffset(payment.getPaidAt()));
  }
}
