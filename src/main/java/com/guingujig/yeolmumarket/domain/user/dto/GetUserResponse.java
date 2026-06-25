package com.guingujig.yeolmumarket.domain.user.dto;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record GetUserResponse(
    Long userId, String nickname, UserRole role, OffsetDateTime createdAt) {

  public static GetUserResponse from(User user) {
    return new GetUserResponse(
        user.getId(),
        user.getNickname(),
        user.getRole(),
        user.getCreatedAt().atOffset(ZoneOffset.UTC));
  }
}
