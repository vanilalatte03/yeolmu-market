package com.guingujig.yeolmumarket.domain.refund.entity;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.entity.BaseTimeEntity;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    name = "refund_request",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_refund_request_order", columnNames = "order_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requester_id", nullable = false)
  private User requester;

  @Column(nullable = false, length = 255)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RefundRequestStatus status;

  @Column(name = "seller_response", length = 255)
  private String sellerResponse;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "rejected_at")
  private LocalDateTime rejectedAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  /**
   * 구매자의 환불 요청을 REQUESTED 상태로 생성한다.
   *
   * <p>사유 정규화와 주문 상태 전이는 서비스 계층에서 선행한다.
   */
  public static RefundRequest create(
      Order order, User requester, String reason, LocalDateTime requestedAt) {
    RefundRequest refundRequest = new RefundRequest();
    refundRequest.order = Objects.requireNonNull(order, "order는 필수입니다.");
    refundRequest.requester = Objects.requireNonNull(requester, "requester는 필수입니다.");
    refundRequest.reason = Objects.requireNonNull(reason, "reason은 필수입니다.");
    refundRequest.status = RefundRequestStatus.REQUESTED;
    refundRequest.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다.");
    return refundRequest;
  }

  /**
   * REQUESTED 상태의 환불 요청을 판매자 승인 결과인 APPROVED로 전이한다.
   *
   * <p>REQUESTED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 재처리를 차단한다.
   */
  public void approve(LocalDateTime approvedAt) {
    validateRequested();
    this.status = RefundRequestStatus.APPROVED;
    this.approvedAt = Objects.requireNonNull(approvedAt, "approvedAt은 필수입니다.");
  }

  /**
   * REQUESTED 상태의 환불 요청을 판매자 거절 결과인 DISPUTED로 전이한다.
   *
   * <p>판매자 거절은 별도 REJECTED 상태를 만들지 않고 분쟁 상태와 선택 입력된 판매자 응답만 기록한다.
   */
  public void rejectToDispute(String sellerResponse, LocalDateTime rejectedAt) {
    validateRequested();
    this.status = RefundRequestStatus.DISPUTED;
    this.sellerResponse = sellerResponse;
    this.rejectedAt = Objects.requireNonNull(rejectedAt, "rejectedAt은 필수입니다.");
  }

  /**
   * DISPUTED 상태의 환불 요청을 분쟁 종료 결과인 CLOSED로 전이한다.
   *
   * <p>선택 입력된 종료 사유가 있으면 판매자 응답으로 기록하고, DISPUTED가 아닌 상태에서 호출하면 {@link BusinessException}을 던진다.
   */
  public void resolveDispute(String sellerResponse, LocalDateTime resolvedAt) {
    validateDisputed();
    this.status = RefundRequestStatus.CLOSED;
    if (sellerResponse != null) {
      this.sellerResponse = sellerResponse;
    }
    this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt은 필수입니다.");
  }

  private void validateRequested() {
    if (this.status != RefundRequestStatus.REQUESTED) {
      throw new BusinessException(ErrorCode.INVALID_REFUND_REQUEST_STATUS);
    }
  }

  private void validateDisputed() {
    if (this.status != RefundRequestStatus.DISPUTED) {
      throw new BusinessException(ErrorCode.INVALID_REFUND_REQUEST_STATUS);
    }
  }
}
