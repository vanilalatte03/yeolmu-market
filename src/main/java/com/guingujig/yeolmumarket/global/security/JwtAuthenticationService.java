package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider.JwtAccessClaims;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationService {

  private final JwtTokenProvider jwtTokenProvider;
  private final RevokedAccessTokenRepository revokedAccessTokenRepository;

  /**
   * access token의 서명, 만료, 폐기 여부를 모두 검증하고 인증 객체를 반환한다.
   *
   * <p>폐기 토큰은 인증 실패로 처리하며, Redis 조회 실패는 호출자가 인증 채널별 응답 형식에 맞게 처리한다.
   */
  public Authentication authenticate(String token) {
    JwtAccessClaims claims = jwtTokenProvider.parseAccessToken(token);
    if (revokedAccessTokenRepository.exists(jwtTokenProvider.hashToken(token))) {
      throw new BusinessException(ErrorCode.REVOKED_TOKEN);
    }
    return createAuthentication(claims);
  }

  private Authentication createAuthentication(JwtAccessClaims claims) {
    AuthenticatedUser principal =
        new AuthenticatedUser(claims.userId(), claims.email(), claims.role());
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
  }
}
