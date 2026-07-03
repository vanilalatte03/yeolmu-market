package com.guingujig.yeolmumarket.domain.review.dto;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ReviewResponse(
    Long reviewId,
    Long orderId,
    Long reviewerId,
    Long revieweeId,
    Integer score,
    String content,
    OffsetDateTime createdAt) {

  public static ReviewResponse from(Review review) {
    return new ReviewResponse(
        review.getId(),
        review.getOrder().getId(),
        review.getReviewer().getId(),
        review.getReviewee().getId(),
        review.getScore(),
        review.getContent(),
        review.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
