package com.guingujig.yeolmumarket.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

class RefreshTokenCookieProviderTest {

  @Test
  void secure_설정이_true이면_쿠키에_Secure_속성을_포함한다() {
    JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    when(jwtTokenProvider.getRefreshTokenValiditySeconds()).thenReturn(1209600L);
    RefreshTokenCookieProvider cookieProvider =
        new RefreshTokenCookieProvider(
            jwtTokenProvider, "refreshToken", "/api/auth/refresh", "Lax", true);

    String setCookie = cookieProvider.createCookie("refresh-token").toString();

    assertThat(setCookie)
        .contains("refreshToken=refresh-token")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Lax")
        .contains("Path=/api/auth/refresh")
        .contains("Max-Age=1209600");
  }
}
