package com.guingujig.yeolmumarket.domain.user.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class UserControllerTest {

  private final WebApplicationContext context;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;
  private MockMvc mockMvc;

  @Autowired
  UserControllerTest(
      WebApplicationContext context,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.context = context;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
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
  void 비인증_요청으로_유저_공개_정보를_조회할_수_있다() throws Exception {
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
}
