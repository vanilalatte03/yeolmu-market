package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RefundResolution;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundLockedCommandService {

  private static final String DUPLICATE_REFUND_REQUEST_CONSTRAINT = "uk_refund_request_order";

  private final OrderRepository orderRepository;
  private final RefundRequestRepository refundRequestRepository;
  private final PaymentRepository paymentRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
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

  @Transactional
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
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());

    return ApproveRefundRequestResponse.from(refundRequest);
  }

  @Transactional
  public RejectRefundRequestResponse rejectRefundRequest(
      Long sellerId, Long refundId, String reason) {
    RefundRequest refundRequest = fetchRefundRequest(refundId);

    LocalDateTime rejectedAt = nowUtc();
    refundRequest.rejectBySeller(sellerId, reason, rejectedAt);

    flushRefundRequestChanges();

    return RejectRefundRequestResponse.from(refundRequest);
  }

  @Transactional
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
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());

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
