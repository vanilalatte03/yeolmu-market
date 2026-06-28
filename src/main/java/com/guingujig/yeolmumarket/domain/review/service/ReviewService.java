package com.guingujig.yeolmumarket.domain.review.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewResponse;
import com.guingujig.yeolmumarket.domain.review.entity.Review;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final OrderRepository orderRepository;

  /**
   * 거래 완료 주문 참여자가 같은 주문의 상대방에게 리뷰를 작성한다.
   *
   * @throws BusinessException VALIDATION_FAILED - 평점 또는 내용이 유효하지 않은 경우
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 참여자가 아닌 사용자의 요청
   * @throws BusinessException REVIEW_NOT_ALLOWED - COMPLETED가 아닌 주문에 리뷰 작성 요청
   * @throws BusinessException REVIEW_ALREADY_EXISTS - 같은 주문에 이미 리뷰를 작성한 경우
   */
  @Transactional
  public ReviewResponse createReview(Long reviewerId, Long orderId, Integer score, String content) {
    validateScore(score);
    String normalizedContent = normalizeContent(content);

    orderRepository
        .findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    ReviewParticipants participants = resolveParticipants(order, reviewerId);
    if (order.getOrderStatus() != OrderStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
    }
    if (reviewRepository.existsByOrderIdAndReviewerId(orderId, reviewerId)) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    Review review =
        Review.create(
            order, participants.reviewer(), participants.reviewee(), score, normalizedContent);

    try {
      reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    return ReviewResponse.from(review);
  }

  private void validateScore(Integer score) {
    if (score == null || score < 1 || score > 5) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  private String normalizeContent(String content) {
    if (content == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedContent = content.trim();
    if (normalizedContent.isBlank() || normalizedContent.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedContent;
  }

  private ReviewParticipants resolveParticipants(Order order, Long reviewerId) {
    User buyer = order.getBuyer();
    User seller = order.getSeller();
    if (Objects.equals(buyer.getId(), reviewerId)) {
      return new ReviewParticipants(buyer, seller);
    }
    if (Objects.equals(seller.getId(), reviewerId)) {
      return new ReviewParticipants(seller, buyer);
    }
    throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
  }

  private record ReviewParticipants(User reviewer, User reviewee) {}
}
