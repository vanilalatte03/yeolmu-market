package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

  private static final String DUPLICATE_REFUND_REQUEST_CONSTRAINT = "uk_refund_request_order";

  private final OrderRepository orderRepository;
  private final RefundRequestRepository refundRequestRepository;

  /**
   * 로그인한 구매자가 SHIPPING 상태 주문에 환불 요청을 생성하고 주문을 REFUND_REQUESTED로 전이한다.
   *
   * <p>주문 row lock으로 동일 주문 환불 요청 생성을 직렬화하고, 성공 시 상품은 RESERVED, 결제는 PAID 상태를 유지한다.
   *
   * @throws BusinessException VALIDATION_FAILED - 사유가 누락, blank, 또는 trim 후 255자를 초과하는 경우
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 구매자가 아닌 사용자의 요청
   * @throws BusinessException REFUND_REQUEST_ALREADY_EXISTS - 이미 환불 요청이 존재하는 주문
   * @throws BusinessException INVALID_ORDER_STATUS - SHIPPING이 아닌 주문의 환불 요청
   */
  @Transactional
  public CreateRefundRequestResponse createRefundRequest(
      Long buyerId, Long orderId, String reason) {
    String normalizedReason = normalizeReason(reason);

    Order order =
        orderRepository
            .findWithDetailsByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    if (!Objects.equals(order.getBuyer().getId(), buyerId)) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }

    if (refundRequestRepository.existsByOrder_Id(orderId)) {
      throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
    }

    order.requestRefund();
    LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    RefundRequest refundRequest =
        RefundRequest.create(order, order.getBuyer(), normalizedReason, requestedAt);

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

  private String normalizeReason(String reason) {
    if (reason == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedReason = reason.trim();
    if (normalizedReason.isBlank() || normalizedReason.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedReason;
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
