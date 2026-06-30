package com.guingujig.yeolmumarket.domain.auth.dto;

public record RefreshTokenResponse(String tokenType, String accessToken, long expiresIn) {
  public static RefreshTokenResponse from(RefreshTokenResult result) {
    return new RefreshTokenResponse(result.tokenType(), result.accessToken(), result.expiresIn());
  }
}
