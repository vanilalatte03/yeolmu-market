package com.guingujig.yeolmumarket.domain.review.dto;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record UpdateReviewResponse(
    Long reviewId, Integer score, String content, OffsetDateTime updatedAt) {

  public static UpdateReviewResponse from(Review review) {
    return new UpdateReviewResponse(
        review.getId(),
        review.getScore(),
        review.getContent(),
        review.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
