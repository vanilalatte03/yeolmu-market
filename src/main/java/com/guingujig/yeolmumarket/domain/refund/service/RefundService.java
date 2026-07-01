package com.guingujig.yeolmumarket.domain.refund.service;

import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
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

  private final RefundRequestRepository refundRequestRepository;

  @Transactional(readOnly = true)
  public Long findOrderIdByRefundId(Long refundId) {
    return refundRequestRepository
        .findOrderIdById(refundId)
        .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public RefundRequest getRefundRequestWithOrderSellerAndProduct(Long refundId) {
    return refundRequestRepository
        .findWithOrderSellerAndProductById(refundId)
        .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public boolean existsByOrderId(Long orderId) {
    return refundRequestRepository.existsByOrder_Id(orderId);
  }

  public void validateNoExistingRefundRequest(Long orderId) {
    if (existsByOrderId(orderId)) {
      throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
    }
  }

  public RefundRequest saveNewRefundRequestAndFlush(RefundRequest refundRequest) {
    try {
      return refundRequestRepository.saveAndFlush(refundRequest);
    } catch (DataIntegrityViolationException e) {
      if (isDuplicateRefundRequestConstraint(e)) {
        throw new BusinessException(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS);
      }
      throw e;
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
  }

  public void flushRefundRequestStatusChange() {
    try {
      refundRequestRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_REFUND_REQUEST_STATUS);
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
