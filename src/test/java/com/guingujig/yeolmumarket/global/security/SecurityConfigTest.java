package com.guingujig.yeolmumarket.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.YeolmuMarketApplication;
import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = {YeolmuMarketApplication.class, SecurityConfigTest.TestController.class})
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  SecurityConfigTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 로그인_API는_인증_없이_호출할_수_있다() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "not-email",
                      "password": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 리프레시_API는_인증_없이_호출할_수_있다() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 보호_API는_JWT가_없으면_401_UNAUTHORIZED로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/security/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 보호_API는_유효한_JWT로_인증_사용자를_식별한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(get("/test/security/me").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(user.getId()))
        .andExpect(jsonPath("$.data.email").value("customer@example.com"))
        .andExpect(jsonPath("$.data.role").value("USER"));
  }

  @Test
  void 보호_API는_서명이_잘못된_JWT면_401_INVALID_TOKEN으로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/security/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void 보호_API는_만료된_JWT면_401_EXPIRED_TOKEN으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String expiredToken = "Bearer " + jwtTokenProvider.issueExpiredAccessToken(user);

    mockMvc
        .perform(get("/test/security/me").header(HttpHeaders.AUTHORIZATION, expiredToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
  }

  @Test
  void 리프레시_API는_만료된_refresh_token이면_401_EXPIRED_TOKEN으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String expiredRefreshToken = jwtTokenProvider.issueExpiredRefreshToken(user);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "%s"
                    }
                    """
                        .formatted(expiredRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
  }

  @Test
  void 보호_API는_refresh_token으로_인증할_수_없다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String refreshToken = "Bearer " + jwtTokenProvider.issueRefreshToken(user);

    mockMvc
        .perform(get("/test/security/me").header(HttpHeaders.AUTHORIZATION, refreshToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void 로그아웃에_성공하면_200과_loggedOut_true를_반환하고_블랙리스트에_등록한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String expectedHash = jwtTokenProvider.hashToken(accessToken);

    mockMvc
        .perform(
            post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.loggedOut").value(true));

    verify(revokedAccessTokenRepository).add(eq(expectedHash), any(Duration.class));
    verify(activeRefreshTokenRepository).deleteByUserId(user.getId());
  }

  @Test
  void 로그아웃_후_refresh_token으로_재발급하면_401_REVOKED_TOKEN으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String refreshToken = jwtTokenProvider.issueRefreshToken(user);
    JwtTokenProvider.JwtRefreshClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);

    mockMvc
        .perform(
            post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.loggedOut").value(true));
    verify(activeRefreshTokenRepository).deleteByUserId(user.getId());

    when(activeRefreshTokenRepository.rotate(
            eq(user.getId()),
            eq(claims.jti()),
            org.mockito.ArgumentMatchers.anyString(),
            any(Duration.class)))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "%s"
                    }
                    """
                        .formatted(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVOKED_TOKEN"));
  }

  @Test
  void 보호_API는_블랙리스트에_등록된_JWT면_401_REVOKED_TOKEN으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    when(revokedAccessTokenRepository.exists(jwtTokenProvider.hashToken(accessToken)))
        .thenReturn(true);

    mockMvc
        .perform(
            get("/test/security/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVOKED_TOKEN"));
  }

  @Test
  void 관리자_API는_USER_권한이면_403으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @RestController
  static class TestController {

    @GetMapping("/test/security/me")
    ApiResponse<TestUserResponse> me(Authentication authentication) {
      AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
      return ApiResponse.success(new TestUserResponse(user.userId(), user.email(), user.role()));
    }

    @GetMapping("/api/admin/test")
    ApiResponse<Void> admin() {
      return ApiResponse.emptySuccess();
    }
  }

  record TestUserResponse(Long userId, String email, UserRole role) {}
}
