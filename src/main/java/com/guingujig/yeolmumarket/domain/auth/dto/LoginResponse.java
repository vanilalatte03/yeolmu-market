package com.guingujig.yeolmumarket.domain.auth.dto;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;

public record LoginResponse(
    String tokenType, String accessToken, long expiresIn, LoginUserInfo user) {

  public record LoginUserInfo(Long userId, String email, String nickname, UserRole role) {
    public static LoginUserInfo from(User user) {
      return new LoginUserInfo(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
  }
}
