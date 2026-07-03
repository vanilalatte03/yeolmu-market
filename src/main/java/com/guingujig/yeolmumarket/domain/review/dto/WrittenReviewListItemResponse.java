package com.guingujig.yeolmumarket.domain.review.dto;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record WrittenReviewListItemResponse(
    Long reviewId,
    Long orderId,
    String revieweeNickname,
    Integer score,
    String content,
    OffsetDateTime createdAt) {

  public static WrittenReviewListItemResponse from(Review review) {
    return new WrittenReviewListItemResponse(
        review.getId(),
        review.getOrder().getId(),
        review.getReviewee().getNickname(),
        review.getScore(),
        review.getContent(),
        review.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
