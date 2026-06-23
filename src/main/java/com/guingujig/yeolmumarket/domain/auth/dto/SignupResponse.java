package com.guingujig.yeolmumarket.domain.auth.dto;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record SignupResponse(
    Long userId, String email, String nickname, UserRole role, OffsetDateTime createdAt) {

  public static SignupResponse from(User user) {
    OffsetDateTime createdAt =
        user.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    return new SignupResponse(
        user.getId(), user.getEmail(), user.getNickname(), user.getRole(), createdAt);
  }
}
