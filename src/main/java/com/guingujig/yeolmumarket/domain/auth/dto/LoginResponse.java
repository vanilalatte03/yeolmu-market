package com.guingujig.yeolmumarket.domain.auth.dto;

public record LoginResponse(
    String tokenType, String accessToken, long expiresIn, LoginUserInfo user) {
  public static LoginResponse from(LoginTokenResult result) {
    return new LoginResponse(
        result.tokenType(), result.accessToken(), result.expiresIn(), result.user());
  }
}
