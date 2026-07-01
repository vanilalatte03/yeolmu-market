package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.payment.dto.PaymentDetailResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentStatusResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;

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

  @Transactional(readOnly = true)
  public Long getOrderId(Long paymentId) {
    return paymentRepository
        .findOrderIdById(paymentId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
  }

  public Optional<Payment> findByOrderId(Long orderId) {
    return paymentRepository.findByOrder_Id(orderId);
  }

  public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
    return paymentRepository.findByIdempotencyKey(idempotencyKey);
  }

  public Payment saveAndFlush(Payment payment) {
    return paymentRepository.saveAndFlush(payment);
  }

  public Payment getPaymentForCancel(Long paymentId) {
    return paymentRepository
        .findWithOrderBuyerSellerAndProductById(paymentId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
  }

  public void flush() {
    paymentRepository.flush();
  }

  private Payment fetchWithAuthCheck(Long userId, Long paymentId) {
    Payment payment =
        paymentRepository
            .findWithOrderAndUsersById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

    payment.validateParticipant(userId);
    return payment;
  }
}
