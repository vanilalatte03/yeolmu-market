package com.guingujig.yeolmumarket.domain.review.dto;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ReceivedReviewListItemResponse(
    Long reviewId,
    Long orderId,
    String reviewerNickname,
    Integer score,
    String content,
    OffsetDateTime createdAt) {

  public static ReceivedReviewListItemResponse from(Review review) {
    return new ReceivedReviewListItemResponse(
        review.getId(),
        review.getOrder().getId(),
        review.getReviewer().getNickname(),
        review.getScore(),
        review.getContent(),
        review.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
