package com.guingujig.yeolmumarket.domain.user.dto;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record UpdateUserResponse(
    Long userId, String email, String nickname, UserRole role, OffsetDateTime updatedAt) {

  public static UpdateUserResponse from(User user) {
    return new UpdateUserResponse(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getRole(),
        user.getModifiedAt().atOffset(ZoneOffset.UTC));
  }
}
