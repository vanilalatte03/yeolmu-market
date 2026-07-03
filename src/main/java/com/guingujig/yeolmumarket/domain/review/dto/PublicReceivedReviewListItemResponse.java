package com.guingujig.yeolmumarket.domain.review.dto;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record PublicReceivedReviewListItemResponse(
    Long reviewId,
    String reviewerNickname,
    Integer score,
    String content,
    OffsetDateTime createdAt) {

  public static PublicReceivedReviewListItemResponse from(Review review) {
    return new PublicReceivedReviewListItemResponse(
        review.getId(),
        review.getReviewer().getNickname(),
        review.getScore(),
        review.getContent(),
        review.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
