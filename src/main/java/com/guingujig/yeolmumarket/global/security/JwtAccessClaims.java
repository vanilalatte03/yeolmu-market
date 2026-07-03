package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.user.entity.UserRole;

public record JwtAccessClaims(Long userId, String email, UserRole role) {}
