package com.guingujig.yeolmumarket.domain.order.entity;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "buyer_id", nullable = false)
  private User buyer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private User seller;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_status", nullable = false, length = 20)
  private OrderStatus orderStatus;

  @Column(name = "order_price", nullable = false)
  private Integer orderPrice;

  @Column(name = "tracking_number", length = 100)
  private String trackingNumber;

  @Column(name = "shipped_at")
  private LocalDateTime shippedAt;

  /**
   * 주문 생성 시점의 상품 가격을 스냅샷으로 저장하고 CREATED 상태로 초기화한다.
   *
   * <p>seller는 product.getSeller()에서 직접 세팅해 상품 판매자와 주문 판매자의 불일치를 원천 차단한다.
   */
  public static Order create(User buyer, Product product) {
    Order order = new Order();
    order.buyer = Objects.requireNonNull(buyer, "buyer는 필수입니다.");
    order.product = Objects.requireNonNull(product, "product는 필수입니다.");
    order.seller = product.getSeller();
    order.orderStatus = OrderStatus.CREATED;
    order.orderPrice = product.getPrice();
    return order;
  }

  /**
   * CREATED 상태의 주문을 PAID로 전이한다.
   *
   * <p>CREATED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void markAsPaid() {
    if (this.orderStatus != OrderStatus.CREATED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.PAID;
  }

  /** CREATED 주문을 결제 실패 결과로 취소하고 상품 예약을 해제한다. */
  public void failPaymentAndReleaseProduct() {
    cancel();
    product.cancelReservation();
  }

  /**
   * CREATED 상태의 주문을 CANCELED로 전이한다.
   *
   * <p>CREATED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void cancel() {
    if (this.orderStatus != OrderStatus.CREATED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.CANCELED;
  }

  /** CREATED 주문을 취소하고 예약된 상품을 다시 판매 중으로 되돌린다. */
  public void cancelAndReleaseProduct() {
    cancel();
    product.cancelReservation();
  }

  /**
   * PAID 상태의 주문을 결제 취소 결과인 REFUNDED로 전이한다.
   *
   * <p>PAID가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void cancelPaidPayment() {
    if (this.orderStatus != OrderStatus.PAID) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.REFUNDED;
  }

  /** PAID 주문을 결제 환불 결과로 전이하고 예약된 상품을 다시 판매 중으로 되돌린다. */
  public void refundPaidPaymentAndReleaseProduct() {
    cancelPaidPayment();
    product.cancelReservation();
  }

  /**
   * PAID 상태의 주문에 배송 증빙을 기록하고 SHIPPING으로 전이한다.
   *
   * <p>PAID가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void registerShipping(String trackingNumber, LocalDateTime shippedAt) {
    if (this.orderStatus != OrderStatus.PAID) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber는 필수입니다.");
    this.shippedAt = Objects.requireNonNull(shippedAt, "shippedAt은 필수입니다.");
    this.orderStatus = OrderStatus.SHIPPING;
  }

  /**
   * SHIPPING 상태의 주문을 구매확정 결과인 COMPLETED로 전이한다.
   *
   * <p>SHIPPING이 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void confirmPurchase() {
    if (this.orderStatus != OrderStatus.SHIPPING) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.COMPLETED;
  }

  /** SHIPPING 주문을 구매 확정하고 상품을 판매 완료 상태로 전이한다. */
  public void confirmPurchaseAndCompleteProduct() {
    if (this.orderStatus != OrderStatus.SHIPPING || !product.isReserved()) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.COMPLETED;
    product.completeSale();
  }

  /**
   * SHIPPING 상태의 주문을 환불 요청 상태로 전이한다.
   *
   * <p>SHIPPING이 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void requestRefund() {
    if (this.orderStatus != OrderStatus.SHIPPING) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.REFUND_REQUESTED;
  }

  /**
   * REFUND_REQUESTED 상태의 주문을 환불 승인 결과인 REFUNDED로 전이한다.
   *
   * <p>REFUND_REQUESTED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void approveRefund() {
    if (this.orderStatus != OrderStatus.REFUND_REQUESTED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.REFUNDED;
  }

  /** REFUND_REQUESTED 주문을 환불 승인 결과로 전이하고 상품 예약을 해제한다. */
  public void approveRefundAndReleaseProduct() {
    approveRefund();
    product.cancelReservation();
  }

  /**
   * REFUND_REQUESTED 상태의 주문을 환불 거절 결과인 DISPUTED로 전이한다.
   *
   * <p>REFUND_REQUESTED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void rejectRefund() {
    if (this.orderStatus != OrderStatus.REFUND_REQUESTED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.DISPUTED;
  }

  /**
   * DISPUTED 상태의 주문을 분쟁 환불 결과인 REFUNDED로 전이한다.
   *
   * <p>DISPUTED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void refundDispute() {
    if (this.orderStatus != OrderStatus.DISPUTED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.REFUNDED;
  }

  /** DISPUTED 주문을 분쟁 환불 결과로 전이하고 상품 예약을 해제한다. */
  public void refundDisputeAndReleaseProduct() {
    refundDispute();
    product.cancelReservation();
  }

  /**
   * DISPUTED 상태의 주문을 분쟁 거래 완료 결과인 COMPLETED로 전이한다.
   *
   * <p>DISPUTED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void completeDispute() {
    if (this.orderStatus != OrderStatus.DISPUTED) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.COMPLETED;
  }

  /** DISPUTED 주문을 분쟁 거래 완료 결과로 전이하고 상품을 판매 완료 상태로 전이한다. */
  public void completeDisputeAndCompleteProduct() {
    if (this.orderStatus != OrderStatus.DISPUTED || !product.isReserved()) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
    }
    this.orderStatus = OrderStatus.COMPLETED;
    product.completeSale();
  }

  public void validateBuyer(Long userId) {
    validateBuyer(userId, ErrorCode.ORDER_ACCESS_DENIED);
  }

  public void validateBuyer(Long userId, ErrorCode errorCode) {
    if (!isBuyer(userId)) {
      throw new BusinessException(errorCode);
    }
  }

  public void validateSeller(Long userId) {
    validateSeller(userId, ErrorCode.ORDER_ACCESS_DENIED);
  }

  public void validateSeller(Long userId, ErrorCode errorCode) {
    if (!isSeller(userId)) {
      throw new BusinessException(errorCode);
    }
  }

  public void validateParticipant(Long userId) {
    validateParticipant(userId, ErrorCode.ORDER_ACCESS_DENIED);
  }

  public void validateParticipant(Long userId, ErrorCode errorCode) {
    if (!isBuyer(userId) && !isSeller(userId)) {
      throw new BusinessException(errorCode);
    }
  }

  public boolean isCreated() {
    return orderStatus == OrderStatus.CREATED;
  }

  public boolean isPaid() {
    return orderStatus == OrderStatus.PAID;
  }

  public boolean isDisputed() {
    return orderStatus == OrderStatus.DISPUTED;
  }

  /**
   * 거래 완료 주문에서 리뷰 작성자와 대상자를 결정한다.
   *
   * <p>주문 참여자가 아니면 접근 거부, 거래 완료 전이면 리뷰 불가로 실패한다.
   */
  public ReviewParticipants resolveReviewParticipants(Long reviewerId) {
    ReviewParticipants participants;
    if (isBuyer(reviewerId)) {
      participants = new ReviewParticipants(buyer, seller);
    } else if (isSeller(reviewerId)) {
      participants = new ReviewParticipants(seller, buyer);
    } else {
      throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }

    if (orderStatus != OrderStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
    }
    return participants;
  }

  private boolean isBuyer(Long userId) {
    return buyer != null && Objects.equals(buyer.getId(), userId);
  }

  private boolean isSeller(Long userId) {
    return seller != null && Objects.equals(seller.getId(), userId);
  }

  public record ReviewParticipants(User reviewer, User reviewee) {}
}
