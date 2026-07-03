package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import org.springframework.security.core.AuthenticatedPrincipal;

public record AuthenticatedUser(Long userId, String email, UserRole role)
    implements AuthenticatedPrincipal {

  /** STOMP 사용자 목적지 라우팅이 HTTP 인증과 같은 사용자 ID 기준으로 동작하도록 principal 이름을 맞춘다. */
  @Override
  public String getName() {
    return userId.toString();
  }
}
