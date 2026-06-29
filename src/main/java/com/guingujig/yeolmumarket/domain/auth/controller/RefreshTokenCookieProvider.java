package com.guingujig.yeolmumarket.domain.auth.controller;

import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieProvider {

  private final JwtTokenProvider jwtTokenProvider;
  private final String name;
  private final String path;
  private final String sameSite;
  private final boolean secure;

  public RefreshTokenCookieProvider(
      JwtTokenProvider jwtTokenProvider,
      @Value("${auth.refresh-token-cookie.name}") String name,
      @Value("${auth.refresh-token-cookie.path}") String path,
      @Value("${auth.refresh-token-cookie.same-site}") String sameSite,
      @Value("${auth.refresh-token-cookie.secure}") boolean secure) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.name = name;
    this.path = path;
    this.sameSite = sameSite;
    this.secure = secure;
  }

  public ResponseCookie createCookie(String refreshToken) {
    return ResponseCookie.from(name, refreshToken)
        .httpOnly(true)
        .secure(secure)
        .sameSite(sameSite)
        .path(path)
        .maxAge(Duration.ofSeconds(jwtTokenProvider.getRefreshTokenValiditySeconds()))
        .build();
  }

  public ResponseCookie deleteCookie() {
    return ResponseCookie.from(name, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(sameSite)
        .path(path)
        .maxAge(Duration.ZERO)
        .build();
  }
}
