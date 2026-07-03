package com.guingujig.yeolmumarket.domain.review.entity;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.entity.BaseTimeEntity;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "review",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_review_order_reviewer",
            columnNames = {"order_id", "reviewer_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewer_id", nullable = false)
  private User reviewer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewee_id", nullable = false)
  private User reviewee;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(nullable = false)
  private Integer score;

  @Column(nullable = false, length = 255)
  private String content;

  /**
   * 리뷰는 거래 완료된 주문의 구매자와 판매자가 서로에게만 작성할 수 있다.
   *
   * <p>거래 완료 여부와 참여자 판정은 주문 엔티티의 리뷰 참여자 결정 규칙을 따른다.
   */
  public static Review create(
      Order order, User reviewer, User reviewee, Integer score, String content) {
    Order requiredOrder = Objects.requireNonNull(order, "주문은 필수입니다.");
    User requiredReviewer = Objects.requireNonNull(reviewer, "리뷰어는 필수입니다.");
    User requiredReviewee = Objects.requireNonNull(reviewee, "리뷰 대상은 필수입니다.");

    validateScore(score);
    validateParticipants(requiredOrder, requiredReviewer, requiredReviewee);

    Review review = new Review();
    review.order = requiredOrder;
    review.reviewer = requiredReviewer;
    review.reviewee = requiredReviewee;
    review.score = score;
    review.content = requireContent(content);
    return review;
  }

  public void update(Integer score, String content) {
    if (score != null) {
      validateScore(score);
      this.score = score;
    }
    if (content != null) {
      this.content = requireContent(content);
    }
  }

  public void validateReviewer(Long reviewerId) {
    if (!sameUserId(reviewer, reviewerId)) {
      throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
    }
  }

  private static void validateScore(Integer score) {
    if (score == null || score < 1 || score > 5) {
      throw new IllegalArgumentException("리뷰 점수는 1점 이상 5점 이하여야 합니다.");
    }
  }

  private static void validateParticipants(Order order, User reviewer, User reviewee) {
    if (sameUser(reviewer, reviewee)) {
      throw new IllegalArgumentException("리뷰어와 리뷰 대상은 서로 달라야 합니다.");
    }
    if (!isOrderParticipant(order, reviewer) || !isOrderParticipant(order, reviewee)) {
      throw new IllegalArgumentException("리뷰어와 리뷰 대상은 같은 주문의 참여자여야 합니다.");
    }
  }

  private static boolean isOrderParticipant(Order order, User user) {
    return sameUser(order.getBuyer(), user) || sameUser(order.getSeller(), user);
  }

  private static boolean sameUser(User left, User right) {
    if (left == null || right == null) {
      return false;
    }
    if (left.getId() != null && right.getId() != null) {
      return Objects.equals(left.getId(), right.getId());
    }
    return left == right;
  }

  private static boolean sameUserId(User user, Long userId) {
    return user != null && Objects.equals(user.getId(), userId);
  }

  private static String requireContent(String content) {
    if (content == null) {
      throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
    }
    String normalizedContent = content.trim();
    if (normalizedContent.isBlank() || normalizedContent.length() > 255) {
      throw new IllegalArgumentException("리뷰 내용은 255자 이하의 필수 값입니다.");
    }
    return normalizedContent;
  }
}
