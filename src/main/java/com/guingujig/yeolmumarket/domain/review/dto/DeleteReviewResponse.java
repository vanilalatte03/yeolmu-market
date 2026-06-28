package com.guingujig.yeolmumarket.domain.review.dto;

public record DeleteReviewResponse(boolean deleted) {

  public static DeleteReviewResponse success() {
    return new DeleteReviewResponse(true);
  }
}
