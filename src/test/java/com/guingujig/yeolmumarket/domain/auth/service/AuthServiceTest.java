package com.guingujig.yeolmumarket.domain.auth.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @Mock private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenProvider,
            activeRefreshTokenRepository,
            revokedAccessTokenRepository);
  }

  @Test
  void 로그아웃하면_refresh_token을_먼저_삭제한_뒤_access_token을_블랙리스트에_등록한다() {
    Long userId = 1L;
    String accessToken = "access-token";
    Duration remainingTtl = Duration.ofSeconds(3600);
    String tokenHash = "token-hash";
    when(jwtTokenProvider.getAccessTokenRemainingTtl(accessToken)).thenReturn(remainingTtl);
    when(jwtTokenProvider.hashToken(accessToken)).thenReturn(tokenHash);

    authService.logout(userId, accessToken);

    InOrder inOrder = inOrder(activeRefreshTokenRepository, revokedAccessTokenRepository);
    inOrder.verify(activeRefreshTokenRepository).deleteByUserId(userId);
    inOrder.verify(revokedAccessTokenRepository).add(tokenHash, remainingTtl);
  }
}
