package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RefundResolution;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
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

    validateOrderBuyer(order, buyerId);

    if (refundRequestRepository.existsByOrder_Id(orderId)) {
      throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
    }

    order.requestRefund();
    LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    RefundRequest refundRequest =
        RefundRequest.create(order, order.getBuyer(), reason, requestedAt);

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

    validateRefundRequestSeller(refundRequest, sellerId);

    Payment payment =
        paymentRepository
            .findByOrder_Id(refundRequest.getOrder().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    LocalDateTime approvedAt = nowUtc();
    refundRequest.approve(approvedAt);
    refundRequest.getOrder().approveRefund();
    refundRequest.getOrder().getProduct().cancelReservation();
    payment.cancelPaid(approvedAt, "환불 요청 승인");

    flushRefundRequestChanges();
    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());

    return ApproveRefundRequestResponse.from(refundRequest);
  }

  @Transactional
  public RejectRefundRequestResponse rejectRefundRequest(
      Long sellerId, Long refundId, String reason) {
    RefundRequest refundRequest = fetchRefundRequest(refundId);

    validateRefundRequestSeller(refundRequest, sellerId);

    LocalDateTime rejectedAt = nowUtc();
    refundRequest.rejectToDispute(reason, rejectedAt);
    refundRequest.getOrder().rejectRefund();

    flushRefundRequestChanges();

    return RejectRefundRequestResponse.from(refundRequest);
  }

  @Transactional
  public ResolveRefundRequestResponse resolveRefundRequest(
      Long sellerId, Long refundId, RefundResolution resolution, String reason) {
    RefundRequest refundRequest = fetchRefundRequest(refundId);

    validateRefundRequestSeller(refundRequest, sellerId);
    validateResolvableDispute(refundRequest);

    Payment payment =
        paymentRepository
            .findByOrder_Id(refundRequest.getOrder().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    LocalDateTime resolvedAt = nowUtc();
    refundRequest.resolveDispute(reason, resolvedAt);

    if (resolution == RefundResolution.REFUND) {
      refundRequest.getOrder().refundDispute();
      refundRequest.getOrder().getProduct().cancelReservation();
      payment.cancelPaid(resolvedAt, "분쟁 환불 종료");
    } else {
      refundRequest.getOrder().completeDispute();
      refundRequest.getOrder().getProduct().completeSale();
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

  private void validateResolvableDispute(RefundRequest refundRequest) {
    if (refundRequest.getStatus() != RefundRequestStatus.DISPUTED
        || refundRequest.getOrder().getOrderStatus() != OrderStatus.DISPUTED) {
      throw new BusinessException(ErrorCode.INVALID_REFUND_REQUEST_STATUS);
    }
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

  private void validateOrderBuyer(Order order, Long buyerId) {
    if (!Objects.equals(order.getBuyer().getId(), buyerId)) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }
  }

  private void validateRefundRequestSeller(RefundRequest refundRequest, Long sellerId) {
    if (!Objects.equals(refundRequest.getOrder().getSeller().getId(), sellerId)) {
      throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
    }
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
