package com.guingujig.yeolmumarket.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.YeolmuMarketApplication;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = {YeolmuMarketApplication.class, SecurityConfigTest.TestController.class})
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

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
  void 보호_API는_JWT가_없으면_401로_응답한다() throws Exception {
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
  void 보호_API는_유효하지_않은_JWT면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/security/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
