package com.guingujig.yeolmumarket.domain.review.dto;

public record ReviewRatingSummary(Double averageRating, long reviewCount) {

  public ReviewRatingSummary {
    averageRating = resolveAverageRating(averageRating);
  }

  public static ReviewRatingSummary empty() {
    return new ReviewRatingSummary(0.0, 0);
  }

  private static double resolveAverageRating(Double averageRating) {
    if (averageRating == null) {
      return 0.0;
    }
    return Math.round(averageRating * 10.0) / 10.0;
  }
}
