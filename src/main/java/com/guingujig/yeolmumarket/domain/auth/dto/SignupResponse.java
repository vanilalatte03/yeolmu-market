package com.guingujig.yeolmumarket.domain.auth.dto;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import java.time.LocalDateTime;

public record SignupResponse(
    Long userId, String email, String nickname, UserRole role, LocalDateTime createdAt) {

  public static SignupResponse from(User user) {
    return new SignupResponse(
        user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getCreatedAt());
  }
}
