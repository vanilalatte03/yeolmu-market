package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductChangeEventPublisher;
import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RefundResolution;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.LockBoundedTransactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundLockedCommandService {

  private static final String DUPLICATE_REFUND_REQUEST_CONSTRAINT = "uk_refund_request_order";

  private final OrderRepository orderRepository;
  private final RefundRequestRepository refundRequestRepository;
  private final PaymentRepository paymentRepository;
  private final ProductChangeEventPublisher productChangeEventPublisher;

  @LockBoundedTransactional
  public CreateRefundRequestResponse createRefundRequest(
      Long buyerId, Long orderId, String reason) {
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.validateBuyer(buyerId);

    if (refundRequestRepository.existsByOrder_Id(orderId)) {
      throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
    }

    LocalDateTime requestedAt = nowUtc();
    RefundRequest refundRequest = RefundRequest.createForBuyer(order, buyerId, reason, requestedAt);

    try {
      refundRequestRepository.saveAndFlush(refundRequest);
    } catch (DataIntegrityViolationException e) {
      if (isDuplicateRefundRequestConstraint(e)) {
        throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
      }
      throw e;
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }

    return CreateRefundRequestResponse.from(refundRequest);
  }

  @LockBoundedTransactional
  public ApproveRefundRequestResponse approveRefundRequest(Long sellerId, Long refundId) {
    RefundRequest refundRequest = fetchRefundRequest(refundId);
    refundRequest.validateSeller(sellerId);

    Payment payment =
        paymentRepository
            .findByOrder_Id(refundRequest.getOrderId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    LocalDateTime approvedAt = nowUtc();
    refundRequest.approveBySeller(sellerId, payment, approvedAt);

    flushRefundRequestChanges();
    publishProductStatusChanged(
        refundRequest.getOrder().getProduct().getId(),
        ProductStatus.RESERVED,
        ProductStatus.ON_SALE);

    return ApproveRefundRequestResponse.from(refundRequest);
  }

  @LockBoundedTransactional
  public RejectRefundRequestResponse rejectRefundRequest(
      Long sellerId, Long refundId, String reason) {
    RefundRequest refundRequest = fetchRefundRequest(refundId);

    LocalDateTime rejectedAt = nowUtc();
    refundRequest.rejectBySeller(sellerId, reason, rejectedAt);

    flushRefundRequestChanges();

    return RejectRefundRequestResponse.from(refundRequest);
  }

  @LockBoundedTransactional
  public ResolveRefundRequestResponse resolveRefundRequest(
      Long sellerId, Long refundId, RefundResolution resolution, String reason) {
    RefundRequest refundRequest = fetchRefundRequest(refundId);
    refundRequest.validateDisputeResolvableBySeller(sellerId);

    LocalDateTime resolvedAt = nowUtc();

    if (resolution == RefundResolution.REFUND) {
      Payment payment =
          paymentRepository
              .findByOrder_Id(refundRequest.getOrderId())
              .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
      refundRequest.resolveRefundBySeller(sellerId, payment, reason, resolvedAt);
    } else {
      refundRequest.resolveCompleteBySeller(sellerId, reason, resolvedAt);
    }

    flushRefundRequestChanges();
    publishProductStatusChanged(
        refundRequest.getOrder().getProduct().getId(),
        ProductStatus.RESERVED,
        resolution == RefundResolution.REFUND ? ProductStatus.ON_SALE : ProductStatus.SOLD_OUT);

    return ResolveRefundRequestResponse.from(refundRequest);
  }

  private RefundRequest fetchRefundRequest(Long refundId) {
    return refundRequestRepository
        .findWithOrderSellerAndProductById(refundId)
        .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  private void flushRefundRequestChanges() {
    try {
      refundRequestRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_REFUND_REQUEST_STATUS);
    }
  }

  private LocalDateTime nowUtc() {
    return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private void publishProductStatusChanged(Long productId, ProductStatus... affectedStatuses) {
    productChangeEventPublisher.publishSearchIndexAndDisplayChanged(productId, affectedStatuses);
  }

  private boolean isDuplicateRefundRequestConstraint(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof ConstraintViolationException exception
          && isDuplicateRefundRequestConstraintName(exception.getConstraintName())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean isDuplicateRefundRequestConstraintName(String constraintName) {
    if (constraintName == null) {
      return false;
    }
    String normalizedName = constraintName.replace("`", "").replace("\"", "");
    int qualifierIndex = normalizedName.lastIndexOf('.');
    if (qualifierIndex >= 0) {
      normalizedName = normalizedName.substring(qualifierIndex + 1);
    }
    return DUPLICATE_REFUND_REQUEST_CONSTRAINT.equalsIgnoreCase(normalizedName);
  }
}
