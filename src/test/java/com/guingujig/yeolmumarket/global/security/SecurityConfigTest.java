package com.guingujig.yeolmumarket.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.YeolmuMarketApplication;
import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.config.LocalProductImageStorageProperties;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {YeolmuMarketApplication.class, SecurityConfigTest.TestController.class})
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

  private static final Instant EXPIRED_TOKEN_ISSUED_AT = Instant.EPOCH;

  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final LocalProductImageStorageProperties storageProperties;
  private final ObjectMapper objectMapper;
  private final String jwtSecret;

  @Autowired
  SecurityConfigTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      LocalProductImageStorageProperties storageProperties,
      ObjectMapper objectMapper,
      @Value("${jwt.secret}") String jwtSecret) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.storageProperties = storageProperties;
    this.objectMapper = objectMapper;
    this.jwtSecret = jwtSecret;
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
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 웹소켓_handshake는_HTTP_인증_없이_보안_필터를_통과한다() throws Exception {
    mockMvc.perform(get("/ws")).andExpect(status().isBadRequest());
  }

  @Test
  void 업로드된_상품_이미지는_인증_없이_조회할_수_있다() throws Exception {
    Path imagePath = storageProperties.rootPath().resolve("products/1/security-public-image.png");
    Files.createDirectories(imagePath.getParent());
    Files.write(imagePath, "image".getBytes());

    mockMvc
        .perform(get("/uploads/products/1/security-public-image.png"))
        .andExpect(status().isOk());
  }

  @Test
  void 정적_프론트_리소스는_인증_없이_조회할_수_있다() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().isOk());

    mockMvc
        .perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/app.js")));
    mockMvc.perform(get("/styles.css")).andExpect(status().isOk());
    mockMvc.perform(get("/app.js")).andExpect(status().isOk());
    mockMvc.perform(get("/api.js")).andExpect(status().isOk());
    mockMvc.perform(get("/stomp-client.js")).andExpect(status().isOk());
    mockMvc.perform(get("/assets/radish.svg")).andExpect(status().isOk());
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
  void 내_상품_목록_API는_공개_사용자_상품_패턴보다_우선해_JWT를_요구한다() throws Exception {
    mockMvc
        .perform(get("/api/users/me/products"))
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
    String expiredToken = "Bearer " + issueExpiredAccessToken(user);

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
    String expiredRefreshToken = issueExpiredRefreshToken(user);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", expiredRefreshToken)))
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
        .andExpect(jsonPath("$.data.loggedOut").value(true))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth/refresh")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    verify(revokedAccessTokenRepository).add(eq(expectedHash), any(Duration.class));
    verify(activeRefreshTokenRepository).deleteByUserId(user.getId());
  }

  @Test
  void 로그아웃_후_같은_토큰으로_재호출하면_401_REVOKED_TOKEN으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String expectedHash = jwtTokenProvider.hashToken(accessToken);

    // add 호출 시 exists도 true를 반환하도록 인과 관계를 명시적으로 모사한다.
    doAnswer(
            invocation -> {
              when(revokedAccessTokenRepository.exists(expectedHash)).thenReturn(true);
              return null;
            })
        .when(revokedAccessTokenRepository)
        .add(eq(expectedHash), any(Duration.class));

    mockMvc
        .perform(
            post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.loggedOut").value(true));

    mockMvc
        .perform(
            post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVOKED_TOKEN"));
  }

  @Test
  void 로그아웃_후_refresh_token으로_재발급하면_401_REVOKED_TOKEN으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String refreshToken = jwtTokenProvider.issueRefreshToken(user);

    // deleteByUserId 호출이 실제로 rotate 실패를 유발하는 인과 관계를 명시적으로 모사한다.
    doAnswer(
            invocation -> {
              when(activeRefreshTokenRepository.rotate(
                      eq(user.getId()), anyString(), anyString(), any(Duration.class)))
                  .thenReturn(false);
              return null;
            })
        .when(activeRefreshTokenRepository)
        .deleteByUserId(user.getId());

    mockMvc
        .perform(
            post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.loggedOut").value(true));

    mockMvc
        .perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", refreshToken)))
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
  void Redis_장애_시_유효한_JWT가_있는_요청은_degraded_mode로_인증된다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    when(revokedAccessTokenRepository.exists(jwtTokenProvider.hashToken(accessToken)))
        .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

    mockMvc
        .perform(
            get("/test/security/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(user.getId()))
        .andExpect(jsonPath("$.data.email").value("customer@example.com"))
        .andExpect(jsonPath("$.data.role").value("USER"));
  }

  @Test
  void 로그아웃_중_Redis_저장에_실패하면_503_REDIS_UNAVAILABLE로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    String expectedHash = jwtTokenProvider.hashToken(accessToken);
    doThrow(new RedisConnectionFailureException("Redis unavailable"))
        .when(revokedAccessTokenRepository)
        .add(eq(expectedHash), any(Duration.class));

    mockMvc
        .perform(
            post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REDIS_UNAVAILABLE"));
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

  private String issueExpiredAccessToken(User user) {
    return jwtTokenProviderAt(EXPIRED_TOKEN_ISSUED_AT).issueAccessToken(user);
  }

  private String issueExpiredRefreshToken(User user) {
    return jwtTokenProviderAt(EXPIRED_TOKEN_ISSUED_AT).issueRefreshToken(user);
  }

  private JwtTokenProvider jwtTokenProviderAt(Instant instant) {
    return new JwtTokenProvider(
        objectMapper,
        Clock.fixed(instant, ZoneOffset.UTC),
        jwtSecret,
        jwtTokenProvider.getAccessTokenValiditySeconds(),
        jwtTokenProvider.getRefreshTokenValiditySeconds());
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
