package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.MockPaymentResult;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLockedCommandService {

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public PaymentService.ProcessPaymentResult processPayment(
      Long buyerId, Long orderId, String idempotencyKey, CreatePaymentRequest request) {
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    validateOrderBuyer(order, buyerId, ErrorCode.ORDER_ACCESS_DENIED);

    Optional<Payment> existingByOrder = paymentRepository.findByOrder_Id(orderId);
    if (existingByOrder.isPresent()) {
      Payment existing = existingByOrder.get();
      if (existing.getIdempotencyKey().equals(idempotencyKey)) {
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

    if (order.getOrderStatus() != OrderStatus.CREATED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }

    MockPaymentResult result = resolvePaymentResult(request);
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
    return new PaymentService.ProcessPaymentResult(PaymentResponse.from(payment), true);
  }

  @Transactional
  public CancelPaymentResponse cancelPayment(Long buyerId, Long paymentId, String reason) {
    Payment payment =
        paymentRepository
            .findWithOrderBuyerSellerAndProductById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    Order order = payment.getOrder();

    validateOrderBuyer(order, buyerId, ErrorCode.PAYMENT_ACCESS_DENIED);
    validateCancelable(payment, order);

    LocalDateTime canceledAt = LocalDateTime.now(ZoneOffset.UTC);
    if (payment.getStatus() == PaymentStatus.PENDING) {
      payment.cancelPending(canceledAt, reason);
      order.cancel();
    } else {
      payment.cancelPaid(canceledAt, reason);
      order.cancelPaidPayment();
    }
    order.getProduct().cancelReservation();

    try {
      paymentRepository.flush();
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
    }

    eventPublisher.publishEvent(new ProductSearchCacheEvictionEvent());

    return CancelPaymentResponse.from(payment);
  }

  private void validateCancelable(Payment payment, Order order) {
    boolean pendingCancel =
        payment.getStatus() == PaymentStatus.PENDING
            && order.getOrderStatus() == OrderStatus.CREATED;
    boolean paidCancel =
        payment.getStatus() == PaymentStatus.PAID && order.getOrderStatus() == OrderStatus.PAID;
    if (!pendingCancel && !paidCancel) {
      throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
    }
  }

  private MockPaymentResult resolvePaymentResult(CreatePaymentRequest request) {
    MockPaymentResult result = request.result();
    if (result == null) {
      return MockPaymentResult.PAID;
    }
    return result;
  }

  private void validateOrderBuyer(Order order, Long buyerId, ErrorCode errorCode) {
    if (!Objects.equals(order.getBuyer().getId(), buyerId)) {
      throw new BusinessException(errorCode);
    }
  }
}
