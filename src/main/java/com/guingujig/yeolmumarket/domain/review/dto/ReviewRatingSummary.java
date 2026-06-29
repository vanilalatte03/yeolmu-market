package com.guingujig.yeolmumarket.domain.review.dto;

public record ReviewRatingSummary(Double averageRating, long reviewCount) {

  public ReviewRatingSummary {
    averageRating = averageRating == null ? 0.0 : Math.round(averageRating * 10.0) / 10.0;
  }

  public static ReviewRatingSummary empty() {
    return new ReviewRatingSummary(0.0, 0);
  }
}
