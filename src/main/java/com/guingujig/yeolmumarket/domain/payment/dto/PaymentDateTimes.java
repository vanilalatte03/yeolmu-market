package com.guingujig.yeolmumarket.domain.payment.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class PaymentDateTimes {

  private PaymentDateTimes() {}

  static OffsetDateTime toUtcOffset(LocalDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return dateTime.atOffset(ZoneOffset.UTC);
  }
}
