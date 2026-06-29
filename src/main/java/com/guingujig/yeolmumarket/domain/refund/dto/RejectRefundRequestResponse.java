package com.guingujig.yeolmumarket.domain.refund.dto;

import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record RejectRefundRequestResponse(
    Long refundRequestId,
    Long orderId,
    RefundRequestStatus status,
    OrderStatus orderStatus,
    OffsetDateTime rejectedAt) {

  public static RejectRefundRequestResponse from(RefundRequest refundRequest) {
    return new RejectRefundRequestResponse(
        refundRequest.getId(),
        refundRequest.getOrder().getId(),
        refundRequest.getStatus(),
        refundRequest.getOrder().getOrderStatus(),
        refundRequest.getRejectedAt().atOffset(ZoneOffset.UTC));
  }
}
