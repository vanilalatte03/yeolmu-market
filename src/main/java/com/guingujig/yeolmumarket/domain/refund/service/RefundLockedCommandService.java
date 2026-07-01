package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.service.OrderService;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.service.PaymentService;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductChangeEventPublisher;
import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RefundResolution;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.LockBoundedTransactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundLockedCommandService {

  private final OrderService orderService;
  private final PaymentService paymentService;
  private final RefundService refundService;
  private final ProductChangeEventPublisher productChangeEventPublisher;

  @LockBoundedTransactional
  public CreateRefundRequestResponse createRefundRequest(
      Long buyerId, Long orderId, String reason) {
    Order order = orderService.getExistingOrderWithDetails(orderId);

    order.validateBuyer(buyerId);

    refundService.validateNoExistingRefundRequest(orderId);

    LocalDateTime requestedAt = nowUtc();
    RefundRequest refundRequest = RefundRequest.createForBuyer(order, buyerId, reason, requestedAt);

    refundService.saveNewRefundRequestAndFlush(refundRequest);

    return CreateRefundRequestResponse.from(refundRequest);
  }

  @LockBoundedTransactional
  public ApproveRefundRequestResponse approveRefundRequest(Long sellerId, Long refundId) {
    RefundRequest refundRequest = refundService.getRefundRequestWithOrderSellerAndProduct(refundId);
    refundRequest.validateSeller(sellerId);

    Payment payment =
        paymentService
            .findByOrderId(refundRequest.getOrderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    LocalDateTime approvedAt = nowUtc();
    refundRequest.approveBySeller(sellerId, payment, approvedAt);

    refundService.flushRefundRequestStatusChange();
    publishProductStatusChanged(
        refundRequest.getOrder().getProduct().getId(),
        ProductStatus.RESERVED,
        ProductStatus.ON_SALE);

    return ApproveRefundRequestResponse.from(refundRequest);
  }

  @LockBoundedTransactional
  public RejectRefundRequestResponse rejectRefundRequest(
      Long sellerId, Long refundId, String reason) {
    RefundRequest refundRequest = refundService.getRefundRequestWithOrderSellerAndProduct(refundId);

    LocalDateTime rejectedAt = nowUtc();
    refundRequest.rejectBySeller(sellerId, reason, rejectedAt);

    refundService.flushRefundRequestStatusChange();

    return RejectRefundRequestResponse.from(refundRequest);
  }

  @LockBoundedTransactional
  public ResolveRefundRequestResponse resolveRefundRequest(
      Long sellerId, Long refundId, RefundResolution resolution, String reason) {
    RefundRequest refundRequest = refundService.getRefundRequestWithOrderSellerAndProduct(refundId);
    refundRequest.validateDisputeResolvableBySeller(sellerId);

    LocalDateTime resolvedAt = nowUtc();

    if (resolution == RefundResolution.REFUND) {
      Payment payment =
          paymentService
              .findByOrderId(refundRequest.getOrderId())
              .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
      refundRequest.resolveRefundBySeller(sellerId, payment, reason, resolvedAt);
    } else {
      refundRequest.resolveCompleteBySeller(sellerId, reason, resolvedAt);
    }

    refundService.flushRefundRequestStatusChange();
    publishProductStatusChanged(
        refundRequest.getOrder().getProduct().getId(),
        ProductStatus.RESERVED,
        resolution == RefundResolution.REFUND ? ProductStatus.ON_SALE : ProductStatus.SOLD_OUT);

    return ResolveRefundRequestResponse.from(refundRequest);
  }

  private LocalDateTime nowUtc() {
    return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private void publishProductStatusChanged(Long productId, ProductStatus... affectedStatuses) {
    productChangeEventPublisher.publishSearchIndexAndDisplayChanged(productId, affectedStatuses);
  }
}
