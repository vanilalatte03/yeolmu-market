package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationService {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationService.class);

  private final JwtTokenProvider jwtTokenProvider;
  private final RevokedAccessTokenRepository revokedAccessTokenRepository;

  /**
   * access token의 서명, 만료, 폐기 여부를 검증하고 인증 객체를 반환한다.
   *
   * <p>폐기 토큰은 인증 실패로 처리한다. Redis 블랙리스트 조회가 실패하면 서명과 만료가 유효한 access token은 degraded mode로 인증을 계속한다.
   */
  public Authentication authenticate(String token) {
    JwtAccessClaims claims = jwtTokenProvider.parseAccessToken(token);
    validateNotRevoked(token);
    return createAuthentication(claims);
  }

  private void validateNotRevoked(String token) {
    try {
      if (revokedAccessTokenRepository.exists(jwtTokenProvider.hashToken(token))) {
        throw new BusinessException(ErrorCode.REVOKED_TOKEN);
      }
    } catch (DataAccessException exception) {
      log.warn("Redis 장애로 access token 블랙리스트 검증을 건너뜁니다.", exception);
    }
  }

  private Authentication createAuthentication(JwtAccessClaims claims) {
    AuthenticatedUser principal =
        new AuthenticatedUser(claims.userId(), claims.email(), claims.role());
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
  }
}
