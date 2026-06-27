package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.MockPaymentResult;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentDetailResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentStatusResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;

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
  @Transactional
  public ProcessPaymentResult processPayment(
      Long buyerId, Long orderId, String idempotencyKey, CreatePaymentRequest request) {

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    orderRepository
        .findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    if (!Objects.equals(order.getBuyer().getId(), buyerId)) {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }

    Optional<Payment> existingByOrder = paymentRepository.findByOrder_Id(orderId);
    if (existingByOrder.isPresent()) {
      Payment existing = existingByOrder.get();
      if (existing.getIdempotencyKey().equals(idempotencyKey)) {
        return new ProcessPaymentResult(PaymentResponse.from(existing), false);
      }
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }

    paymentRepository
        .findByIdempotencyKey(idempotencyKey)
        .ifPresent(
            p -> {
              throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
            });

    if (order.getOrderStatus() != OrderStatus.CREATED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }

    MockPaymentResult result = request.result() != null ? request.result() : MockPaymentResult.PAID;
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    Payment payment;
    if (result == MockPaymentResult.PAID) {
      payment = Payment.createPaid(order, request.method(), idempotencyKey, now);
      order.markAsPaid();
    } else {
      payment = Payment.createFailed(order, request.method(), idempotencyKey, now);
      order.cancel();
      order.getProduct().cancelReservation();
      eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());
    }

    try {
      paymentRepository.saveAndFlush(payment);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }
    return new ProcessPaymentResult(PaymentResponse.from(payment), true);
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

  private Payment fetchWithAuthCheck(Long userId, Long paymentId) {
    Payment payment =
        paymentRepository
            .findWithOrderAndUsersById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    Long buyerId = payment.getOrder().getBuyer().getId();
    Long sellerId = payment.getOrder().getSeller().getId();
    if (!Objects.equals(buyerId, userId) && !Objects.equals(sellerId, userId)) {
      throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
    }
    return payment;
  }
}
