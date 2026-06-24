package com.guingujig.yeolmumarket.domain.auth.service;

import com.guingujig.yeolmumarket.domain.auth.dto.LoginRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.LoginResponse;
import com.guingujig.yeolmumarket.domain.auth.dto.SignupRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.SignupResponse;
import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
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
   * <p>발급한 refresh token은 원문을 저장하지 않고 해시값만 Redis에 저장한다. 사용자별 활성 refresh token은 하나만 유지되므로 새 로그인은 기존
   * refresh token을 대체한다.
   */
  @Transactional
  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
    }

    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String refreshToken = jwtTokenProvider.issueRefreshToken(user);
    activeRefreshTokenRepository.save(
        user.getId(),
        jwtTokenProvider.hashToken(refreshToken),
        Duration.ofSeconds(jwtTokenProvider.getRefreshTokenValiditySeconds()));

    return new LoginResponse(
        "Bearer",
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenValiditySeconds(),
        jwtTokenProvider.getRefreshTokenValiditySeconds(),
        LoginResponse.LoginUserInfo.from(user));
  }
}
