package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.MockPaymentResult;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.LockBoundedTransactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentLockedCommandService {

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;

  @LockBoundedTransactional
  public PaymentService.ProcessPaymentResult processPayment(
      Long buyerId, Long orderId, String idempotencyKey, CreatePaymentRequest request) {
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    order.validateBuyer(buyerId);

    Optional<Payment> existingByOrder = paymentRepository.findByOrder_Id(orderId);
    if (existingByOrder.isPresent()) {
      Payment existing = existingByOrder.get();
      if (existing.hasIdempotencyKey(idempotencyKey)) {
        return new PaymentService.ProcessPaymentResult(PaymentResponse.from(existing), false);
      }
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }

    paymentRepository
        .findByIdempotencyKey(idempotencyKey)
        .ifPresent(
            p -> {
              throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
            });

    MockPaymentResult result = resolvePaymentResult(request);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    Payment payment;
    if (result == MockPaymentResult.PAID) {
      payment = Payment.createPaid(order, request.method(), idempotencyKey, now);
      order.markAsPaid();
    } else {
      payment = Payment.createFailed(order, request.method(), idempotencyKey, now);
      order.failPaymentAndReleaseProduct();
      eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());
    }

    try {
      paymentRepository.saveAndFlush(payment);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }
    return new PaymentService.ProcessPaymentResult(PaymentResponse.from(payment), true);
  }

  @LockBoundedTransactional
  public CancelPaymentResponse cancelPayment(Long buyerId, Long paymentId, String reason) {
    Payment payment =
        paymentRepository
            .findWithOrderBuyerSellerAndProductById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    LocalDateTime canceledAt = LocalDateTime.now(ZoneOffset.UTC);
    payment.cancelByBuyer(buyerId, canceledAt, reason);

    try {
      paymentRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
    }

    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());

    return CancelPaymentResponse.from(payment);
  }

  private MockPaymentResult resolvePaymentResult(CreatePaymentRequest request) {
    MockPaymentResult result = request.result();
    if (result == null) {
      return MockPaymentResult.PAID;
    }
    return result;
  }
}
