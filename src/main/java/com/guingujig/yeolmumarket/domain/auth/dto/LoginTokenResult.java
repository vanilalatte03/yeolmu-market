package com.guingujig.yeolmumarket.domain.auth.dto;

public record LoginTokenResult(
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresIn,
    LoginUserInfo user) {}
