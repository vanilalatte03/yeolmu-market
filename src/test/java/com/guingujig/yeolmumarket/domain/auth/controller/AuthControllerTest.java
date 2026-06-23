package com.guingujig.yeolmumarket.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AuthControllerTest {

  private final WebApplicationContext context;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private MockMvc mockMvc;

  @Autowired
  AuthControllerTest(
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
  void 회원가입에_성공하면_회원이_생성되고_201로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "customer@example.com",
                      "password": "Password123!",
                      "nickname": "열무구매자"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.userId").isNumber())
        .andExpect(jsonPath("$.data.email").value("customer@example.com"))
        .andExpect(jsonPath("$.data.nickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.createdAt").exists());

    User user = userRepository.findByEmail("customer@example.com").orElseThrow();
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.getPassword()).isNotEqualTo("Password123!");
    assertThat(passwordEncoder.matches("Password123!", user.getPassword())).isTrue();
  }

  @Test
  void 이미_가입된_이메일이면_409로_응답한다() throws Exception {
    userRepository.save(
        new User("customer@example.com", passwordEncoder.encode("Password123!"), "기존사용자"));

    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "customer@example.com",
                      "password": "Password123!",
                      "nickname": "열무구매자"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void 회원가입_요청값_검증에_실패하면_400으로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "not-email",
                      "password": "short",
                      "nickname": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0]", containsString(": ")));
  }
}
