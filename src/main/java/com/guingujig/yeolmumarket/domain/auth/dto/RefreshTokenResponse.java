package com.guingujig.yeolmumarket.domain.auth.dto;

public record RefreshTokenResponse(
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresIn,
    long refreshExpiresIn) {}
