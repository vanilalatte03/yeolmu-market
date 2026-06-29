package com.guingujig.yeolmumarket.domain.user.dto;

import com.guingujig.yeolmumarket.domain.review.dto.ReviewRatingSummary;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record GetUserResponse(
    Long userId,
    String nickname,
    UserRole role,
    Double averageRating,
    long reviewCount,
    OffsetDateTime createdAt) {

  public static GetUserResponse from(User user, ReviewRatingSummary ratingSummary) {
    return new GetUserResponse(
        user.getId(),
        user.getNickname(),
        user.getRole(),
        ratingSummary.averageRating(),
        ratingSummary.reviewCount(),
        user.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
