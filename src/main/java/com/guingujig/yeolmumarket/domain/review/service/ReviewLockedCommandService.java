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
public class ReviewLockedCommandService {

  private final ReviewRepository reviewRepository;
  private final OrderRepository orderRepository;

  @Transactional
  public ReviewResponse createReview(Long reviewerId, Long orderId, Integer score, String content) {
    Order order =
        orderRepository
            .findWithDetailsById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    if (order.getOrderStatus() != OrderStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
    }
    if (reviewRepository.existsByOrderIdAndReviewerId(orderId, reviewerId)) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    Review review = createReview(order, reviewerId, score, content);

    try {
      reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    return ReviewResponse.from(review);
  }

  private Review createReview(Order order, Long reviewerId, Integer score, String content) {
    User buyer = order.getBuyer();
    User seller = order.getSeller();
    if (Objects.equals(buyer.getId(), reviewerId)) {
      return Review.create(order, buyer, seller, score, content);
    }
    if (Objects.equals(seller.getId(), reviewerId)) {
      return Review.create(order, seller, buyer, score, content);
    }
    throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
  }
}
