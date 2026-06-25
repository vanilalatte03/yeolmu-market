package com.guingujig.yeolmumarket.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  @Autowired
  UserControllerTest(
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
  void 유저_공개_정보_조회에_성공하면_200으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(get("/api/users/{userId}", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.userId").value(user.getId()))
        .andExpect(jsonPath("$.data.nickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 존재하지_않는_유저를_조회하면_404로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/{userId}", 999999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
  }

  @Test
  void Authorization_헤더_없이_유저_공개_정보를_조회할_수_있다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(get("/api/users/{userId}", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(user.getId()));
  }

  @Test
  void 응답에_이메일과_비밀번호가_포함되지_않는다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(get("/api/users/{userId}", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").doesNotExist())
        .andExpect(jsonPath("$.data.password").doesNotExist());
  }

  @Test
  void 닉네임만_수정하면_200과_변경된_닉네임을_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.userId").value(user.getId()))
        .andExpect(jsonPath("$.data.email").value("customer@example.com"))
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 비밀번호만_수정하면_200을_응답하고_새_비밀번호가_해시로_저장된다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"password": "NewPassword123!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("열무구매자"));

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("NewPassword123!", updated.getPassword())).isTrue();
    assertThat(updated.getPassword()).doesNotContain("NewPassword123!");
  }

  @Test
  void 닉네임과_비밀번호를_동시에_수정하면_200과_변경된_정보를_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임", "password": "NewPassword123!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"));

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updated.getNickname()).isEqualTo("새닉네임");
    assertThat(passwordEncoder.matches("NewPassword123!", updated.getPassword())).isTrue();
  }

  @Test
  void 수정할_값이_없는_요청은_400으로_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 미인증_요청은_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 인증된_회원을_찾을_수_없으면_404로_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);
    userRepository.delete(user);
    userRepository.flush();

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
