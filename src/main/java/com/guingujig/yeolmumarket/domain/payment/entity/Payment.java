package com.guingujig.yeolmumarket.domain.payment.entity;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "payment",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_payment_order", columnNames = "order_id"),
      @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = "idempotency_key")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(nullable = false)
  private Integer amount;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "failed_at")
  private LocalDateTime failedAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @Column(name = "cancel_reason", length = 255)
  private String cancelReason;

  /**
   * 결제를 생성하고 즉시 PAID 상태로 확정한다.
   *
   * <p>모의 결제 성공 흐름에서 사용한다. amount는 외부 입력 없이 주문의 orderPrice로 산정한다.
   */
  public static Payment createPaid(
      Order order, PaymentMethod method, String idempotencyKey, LocalDateTime paidAt) {
    Payment payment = new Payment();
    payment.order = Objects.requireNonNull(order);
    payment.method = Objects.requireNonNull(method);
    payment.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    payment.amount = order.getOrderPrice();
    payment.status = PaymentStatus.PAID;
    payment.paidAt = Objects.requireNonNull(paidAt);
    return payment;
  }

  /**
   * 결제를 생성하고 즉시 FAILED 상태로 확정한다.
   *
   * <p>모의 결제 실패 흐름에서 사용한다. amount는 외부 입력 없이 주문의 orderPrice로 산정한다.
   */
  public static Payment createFailed(
      Order order, PaymentMethod method, String idempotencyKey, LocalDateTime failedAt) {
    Payment payment = new Payment();
    payment.order = Objects.requireNonNull(order);
    payment.method = Objects.requireNonNull(method);
    payment.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    payment.amount = order.getOrderPrice();
    payment.status = PaymentStatus.FAILED;
    payment.failedAt = Objects.requireNonNull(failedAt);
    return payment;
  }
}
