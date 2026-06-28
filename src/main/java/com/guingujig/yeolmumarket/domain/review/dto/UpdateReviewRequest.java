package com.guingujig.yeolmumarket.domain.review.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
    @Min(1) @Max(5) Integer score,
    @Size(max = 255, message = "리뷰 내용은 255자 이하여야 합니다.") String content) {

  @AssertTrue(message = "수정할 평점 또는 내용이 필요합니다.")
  public boolean hasUpdatableValue() {
    return score != null || content != null;
  }
}
