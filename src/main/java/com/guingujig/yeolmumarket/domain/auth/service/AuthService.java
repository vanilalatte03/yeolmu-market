package com.guingujig.yeolmumarket.domain.auth.service;

import com.guingujig.yeolmumarket.domain.auth.dto.LoginRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.LoginTokenResult;
import com.guingujig.yeolmumarket.domain.auth.dto.LoginUserInfo;
import com.guingujig.yeolmumarket.domain.auth.dto.RefreshTokenResult;
import com.guingujig.yeolmumarket.domain.auth.dto.SignupRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.SignupResponse;
import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.JwtException;
import com.guingujig.yeolmumarket.global.security.JwtRefreshClaims;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final ActiveRefreshTokenRepository activeRefreshTokenRepository;
  private final RevokedAccessTokenRepository revokedAccessTokenRepository;

  /**
   * 이메일 중복을 검증한 뒤 신규 회원을 생성한다.
   *
   * <p>비밀번호는 저장 전에 해시하며, 신규 회원의 기본 권한은 {@code USER}다.
   */
  @Transactional
  public SignupResponse signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    User user =
        new User(request.email(), passwordEncoder.encode(request.password()), request.nickname());
    User savedUser = userRepository.save(user);
    return SignupResponse.from(savedUser);
  }

  /**
   * 이메일과 비밀번호를 검증하고 access token과 refresh token을 함께 발급한다.
   *
   * <p>refresh token 원문은 쿠키 설정을 위해 호출자에게만 전달하고, Redis에는 jti만 저장한다. 사용자별 활성 refresh token은 하나만 유지되므로
   * 새 로그인은 기존 refresh token을 대체한다.
   */
  @Transactional
  public LoginTokenResult login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS));

    validateLoginPassword(request, user);

    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String refreshToken = jwtTokenProvider.issueRefreshToken(user);
    JwtRefreshClaims refreshClaims = parseRefreshClaims(refreshToken);
    try {
      activeRefreshTokenRepository.save(
          user.getId(),
          refreshClaims.jti(),
          Duration.ofSeconds(jwtTokenProvider.getRefreshTokenValiditySeconds()));
    } catch (DataAccessException exception) {
      throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }

    return new LoginTokenResult(
        "Bearer",
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenValiditySeconds(),
        jwtTokenProvider.getRefreshTokenValiditySeconds(),
        LoginUserInfo.from(user));
  }

  /**
   * 활성 refresh token을 검증한 뒤 새 access token과 refresh token을 발급한다.
   *
   * <p>재발급에 성공하면 Redis Lua script로 기존 refresh token jti를 새 jti로 원자적으로 교체해 이전 refresh token 재사용을
   * 막는다.
   */
  @Transactional
  public RefreshTokenResult refreshToken(String currentRefreshToken) {
    JwtRefreshClaims claims = parseRefreshClaims(currentRefreshToken);

    User user =
        userRepository
            .findById(claims.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String newRefreshToken = jwtTokenProvider.issueRefreshToken(user);
    JwtRefreshClaims newRefreshClaims = parseRefreshClaims(newRefreshToken);
    boolean rotated;
    try {
      rotated =
          activeRefreshTokenRepository.rotate(
              user.getId(),
              claims.jti(),
              newRefreshClaims.jti(),
              Duration.ofSeconds(jwtTokenProvider.getRefreshTokenValiditySeconds()));
    } catch (DataAccessException exception) {
      throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }

    validateRefreshTokenRotated(rotated);

    return new RefreshTokenResult(
        "Bearer",
        accessToken,
        newRefreshToken,
        jwtTokenProvider.getAccessTokenValiditySeconds(),
        jwtTokenProvider.getRefreshTokenValiditySeconds());
  }

  /**
   * 로그아웃 요청에 사용된 access token을 폐기하고, 사용자의 활성 refresh token을 삭제한다.
   *
   * <p>access token을 먼저 폐기하면 이후 실패 시 같은 토큰으로 재시도할 수 없으므로, refresh token 삭제를 먼저 수행한다.
   */
  public void logout(Long userId, String accessToken) {
    try {
      activeRefreshTokenRepository.deleteByUserId(userId);
      revokeAccessTokenIfAlive(accessToken);
    } catch (DataAccessException exception) {
      throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }
  }

  private void revokeAccessTokenIfAlive(String accessToken) {
    try {
      Duration remaining = jwtTokenProvider.getAccessTokenRemainingTtl(accessToken);
      revokedAccessTokenRepository.add(jwtTokenProvider.hashToken(accessToken), remaining);
    } catch (JwtException ignored) {
      // 필터 통과 직후 만료 경계에 걸린 경우: 이미 만료된 토큰은 블랙리스트 등록 불필요
    }
  }

  private JwtRefreshClaims parseRefreshClaims(String refreshToken) {
    try {
      return jwtTokenProvider.parseRefreshToken(refreshToken);
    } catch (JwtException exception) {
      throw new BusinessException(exception.getErrorCode());
    }
  }

  private void validateLoginPassword(LoginRequest request, User user) {
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
    }
  }

  private void validateRefreshTokenRotated(boolean rotated) {
    if (!rotated) {
      throw new BusinessException(ErrorCode.REVOKED_TOKEN);
    }
  }
}
