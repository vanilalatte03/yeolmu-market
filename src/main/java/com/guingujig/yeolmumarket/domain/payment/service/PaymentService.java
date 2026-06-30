package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentDetailResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentStatusResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
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
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final DistributedLockExecutor distributedLockExecutor;
  private final PaymentLockedCommandService paymentLockedCommandService;

  /**
   * 구매자가 CREATED 상태의 주문에 대해 모의 결제를 요청한다.
   *
   * <p>멱등키로 중복 결제를 막는다. 같은 주문·같은 멱등키 재요청은 기존 결제를 그대로 반환한다. 결제 생성, 주문 상태 변경, 상품 상태 변경을 하나의 트랜잭션에서
   * 처리한다. 결제 실패 시 상품이 ON_SALE로 전이되면 검색 캐시 무효화 이벤트를 발행한다.
   *
   * @return created=true이면 신규 결제(201), false이면 멱등 재요청(200)
   * @throws BusinessException VALIDATION_FAILED - 멱등키가 누락 또는 blank인 경우
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 구매자가 아닌 사용자의 요청
   * @throws BusinessException PAYMENT_ALREADY_EXISTS - 다른 멱등키로 같은 주문 재요청 또는 이미 사용된 멱등키
   * @throws BusinessException INVALID_ORDER_STATUS - CREATED가 아닌 주문에 결제 요청
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProcessPaymentResult processPayment(
      Long buyerId, Long orderId, String idempotencyKey, CreatePaymentRequest request) {

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () ->
            paymentLockedCommandService.processPayment(buyerId, orderId, idempotencyKey, request));
  }

  public record ProcessPaymentResult(PaymentResponse response, boolean created) {}

  @Transactional(readOnly = true)
  public PaymentStatusResponse getPaymentStatus(Long userId, Long paymentId) {
    Payment payment = fetchWithAuthCheck(userId, paymentId);
    return PaymentStatusResponse.from(payment);
  }

  @Transactional(readOnly = true)
  public PaymentDetailResponse getPaymentDetail(Long userId, Long paymentId) {
    Payment payment = fetchWithAuthCheck(userId, paymentId);
    return PaymentDetailResponse.from(payment);
  }

  /**
   * 로그인한 주문 구매자가 배송 증빙 등록 전 모의 결제를 취소한다.
   *
   * <p>PENDING 결제는 결제/주문을 CANCELED로, PAID 결제는 결제/주문을 REFUNDED로 전이하고 상품 예약을 해제한다. 결제, 주문, 상품 상태 변경은
   * 하나의 트랜잭션에서 처리하며 상품이 ON_SALE로 복귀하면 검색 캐시 무효화 이벤트를 발행한다.
   *
   * @throws BusinessException VALIDATION_FAILED - trim한 취소 사유가 255자를 초과하는 경우
   * @throws BusinessException PAYMENT_NOT_FOUND - 결제가 존재하지 않는 경우
   * @throws BusinessException PAYMENT_ACCESS_DENIED - 주문 구매자가 아닌 사용자의 취소 요청
   * @throws BusinessException INVALID_PAYMENT_STATUS - 취소할 수 없는 결제 또는 주문 상태, 또는 동시 취소 경합
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CancelPaymentResponse cancelPayment(Long buyerId, Long paymentId, String reason) {
    String normalizedReason = normalizeCancelReason(reason);
    Long orderId =
        paymentRepository
            .findOrderIdById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () -> paymentLockedCommandService.cancelPayment(buyerId, paymentId, normalizedReason));
  }

  private Payment fetchWithAuthCheck(Long userId, Long paymentId) {
    Payment payment =
        paymentRepository
            .findWithOrderAndUsersById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    payment.validateParticipant(userId);
    return payment;
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
