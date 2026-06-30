package com.guingujig.yeolmumarket.domain.review.service;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewResponse;
import com.guingujig.yeolmumarket.domain.review.entity.Review;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.LockBoundedTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewLockedCommandService {

  private final ReviewRepository reviewRepository;
  private final OrderRepository orderRepository;

  @LockBoundedTransactional
  public ReviewResponse createReview(Long reviewerId, Long orderId, Integer score, String content) {
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    Order.ReviewParticipants participants = order.resolveReviewParticipants(reviewerId);
    if (reviewRepository.existsByOrderIdAndReviewerId(orderId, reviewerId)) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    Review review =
        Review.create(order, participants.reviewer(), participants.reviewee(), score, content);

    try {
      reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    return ReviewResponse.from(review);
  }
}
