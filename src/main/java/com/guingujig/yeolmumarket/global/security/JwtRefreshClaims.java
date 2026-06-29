package com.guingujig.yeolmumarket.global.security;

public record JwtRefreshClaims(Long userId, String jti, long expiresAtEpochSeconds) {}
