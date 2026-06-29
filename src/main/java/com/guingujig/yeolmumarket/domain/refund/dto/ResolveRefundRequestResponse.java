package com.guingujig.yeolmumarket.domain.refund.dto;

import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ResolveRefundRequestResponse(
    Long refundRequestId,
    Long orderId,
    RefundRequestStatus status,
    OrderStatus orderStatus,
    ProductStatus productStatus,
    OffsetDateTime resolvedAt) {

  public static ResolveRefundRequestResponse from(RefundRequest refundRequest) {
    return new ResolveRefundRequestResponse(
        refundRequest.getId(),
        refundRequest.getOrder().getId(),
        refundRequest.getStatus(),
        refundRequest.getOrder().getOrderStatus(),
        refundRequest.getOrder().getProduct().getStatus(),
        refundRequest.getResolvedAt().atOffset(ZoneOffset.UTC));
  }
}
