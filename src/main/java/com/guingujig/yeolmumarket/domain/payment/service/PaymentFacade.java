package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentDetailResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentStatusResponse;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.DistributedLockExecutor;
import com.guingujig.yeolmumarket.global.lock.LockKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentFacade {

  private final PaymentService paymentService;
  private final DistributedLockExecutor distributedLockExecutor;
  private final PaymentLockedCommandService paymentLockedCommandService;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProcessPaymentResult processPayment(ProcessPaymentCommand command) {
    validateIdempotencyKey(command.idempotencyKey());

    return distributedLockExecutor.execute(
        LockKeys.order(command.orderId()),
        () -> paymentLockedCommandService.processPayment(command));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CancelPaymentResponse cancelPayment(CancelPaymentCommand command) {
    String normalizedReason = normalizeCancelReason(command.reason());
    Long orderId = paymentService.getOrderId(command.paymentId());

    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () ->
            paymentLockedCommandService.cancelPayment(
                new CancelPaymentCommand(
                    command.buyerId(), command.paymentId(), normalizedReason)));
  }

  @Transactional(readOnly = true)
  public PaymentStatusResponse getPaymentStatus(Long userId, Long paymentId) {
    return paymentService.getPaymentStatus(userId, paymentId);
  }

  @Transactional(readOnly = true)
  public PaymentDetailResponse getPaymentDetail(Long userId, Long paymentId) {
    return paymentService.getPaymentDetail(userId, paymentId);
  }

  private void validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  private String normalizeCancelReason(String reason) {
    if (reason == null) {
      return null;
    }
    String trimmedReason = reason.trim();
    if (trimmedReason.isBlank()) {
      return null;
    }
    if (trimmedReason.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return trimmedReason;
  }
}
