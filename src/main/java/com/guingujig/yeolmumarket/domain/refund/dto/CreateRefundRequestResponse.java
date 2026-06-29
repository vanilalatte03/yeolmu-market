package com.guingujig.yeolmumarket.domain.refund.dto;

import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CreateRefundRequestResponse(
    Long refundRequestId,
    Long orderId,
    RefundRequestStatus status,
    OrderStatus orderStatus,
    OffsetDateTime requestedAt) {

  public static CreateRefundRequestResponse from(RefundRequest refundRequest) {
    return new CreateRefundRequestResponse(
        refundRequest.getId(),
        refundRequest.getOrder().getId(),
        refundRequest.getStatus(),
        refundRequest.getOrder().getOrderStatus(),
        refundRequest.getRequestedAt().atOffset(ZoneOffset.UTC));
  }
}
