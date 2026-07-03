package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.service.OrderService;
import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.MockPaymentResult;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.service.ProductChangeEventPublisher;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.LockBoundedTransactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentLockedCommandService {

  private final PaymentService paymentService;
  private final OrderService orderService;
  private final ProductChangeEventPublisher productChangeEventPublisher;

  @LockBoundedTransactional
  public ProcessPaymentResult processPayment(ProcessPaymentCommand command) {
    Order order = orderService.getExistingOrderWithDetails(command.orderId());

    order.validateBuyer(command.buyerId());

    Optional<Payment> existingByOrder = paymentService.findByOrderId(command.orderId());
    if (existingByOrder.isPresent()) {
      Payment existing = existingByOrder.get();
      if (existing.hasIdempotencyKey(command.idempotencyKey())) {
        return new ProcessPaymentResult(PaymentResponse.from(existing), false);
      }
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }

    paymentService
        .findByIdempotencyKey(command.idempotencyKey())
        .ifPresent(
            p -> {
              throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
            });

    MockPaymentResult result = resolvePaymentResult(command);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    Payment payment;
    if (result == MockPaymentResult.PAID) {
      payment =
          Payment.createPaid(order, command.request().method(), command.idempotencyKey(), now);
      order.markAsPaid();
    } else {
      payment =
          Payment.createFailed(order, command.request().method(), command.idempotencyKey(), now);
      order.failPaymentAndReleaseProduct();
      publishProductStatusChanged(
          order.getProduct().getId(), ProductStatus.RESERVED, ProductStatus.ON_SALE);
    }

    try {
      paymentService.saveAndFlush(payment);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }
    return new ProcessPaymentResult(PaymentResponse.from(payment), true);
  }

  @LockBoundedTransactional
  public CancelPaymentResponse cancelPayment(CancelPaymentCommand command) {
    Payment payment = paymentService.getPaymentForCancel(command.paymentId());

    LocalDateTime canceledAt = LocalDateTime.now(ZoneOffset.UTC);
    payment.cancelByBuyer(command.buyerId(), canceledAt, command.reason());

    try {
      paymentService.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
    }

    publishProductStatusChanged(
        payment.getOrder().getProduct().getId(), ProductStatus.RESERVED, ProductStatus.ON_SALE);

    return CancelPaymentResponse.from(payment);
  }

  private MockPaymentResult resolvePaymentResult(ProcessPaymentCommand command) {
    MockPaymentResult result = command.request().result();
    if (result == null) {
      return MockPaymentResult.PAID;
    }
    return result;
  }

  private void publishProductStatusChanged(Long productId, ProductStatus... affectedStatuses) {
    productChangeEventPublisher.publishSearchIndexAndDisplayChanged(productId, affectedStatuses);
  }
}
