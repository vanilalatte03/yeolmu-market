package com.guingujig.yeolmumarket.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AuthControllerTest {

  private final WebApplicationContext context;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  private MockMvc mockMvc;

  @Autowired
  AuthControllerTest(
      WebApplicationContext context,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.context = context;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
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
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));

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

  @Test
  void 로그인에_성공하면_JWT_access_token을_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "email": "customer@example.com",
                      "password": "Password123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken", matchesPattern("[^.]+\\.[^.]+\\.[^.]+")))
            .andExpect(jsonPath("$.data.refreshToken", matchesPattern("[^.]+\\.[^.]+\\.[^.]+")))
            .andExpect(jsonPath("$.data.expiresIn").value(3600))
            .andExpect(jsonPath("$.data.refreshExpiresIn").value(1209600))
            .andExpect(jsonPath("$.data.user.userId").value(user.getId()))
            .andExpect(jsonPath("$.data.user.email").value("customer@example.com"))
            .andExpect(jsonPath("$.data.user.nickname").value("열무구매자"))
            .andExpect(jsonPath("$.data.user.role").value("USER"))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String refreshToken = responseBody.replaceAll("(?s).*\"refreshToken\":\"([^\"]+)\".*", "$1");
    ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
    verify(activeRefreshTokenRepository)
        .save(eq(user.getId()), tokenHashCaptor.capture(), eq(Duration.ofSeconds(1209600)));
    assertThat(tokenHashCaptor.getValue()).isEqualTo(jwtTokenProvider.hashToken(refreshToken));
    assertThat(tokenHashCaptor.getValue()).isNotEqualTo(refreshToken);
  }

  @Test
  void refresh_token_재발급에_성공하면_토큰을_회전한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String oldRefreshToken = jwtTokenProvider.issueRefreshToken(user);
    when(activeRefreshTokenRepository.findHashByUserId(user.getId()))
        .thenReturn(java.util.Optional.of(jwtTokenProvider.hashToken(oldRefreshToken)));

    MvcResult result =
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
                            .formatted(oldRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken", matchesPattern("[^.]+\\.[^.]+\\.[^.]+")))
            .andExpect(jsonPath("$.data.refreshToken", matchesPattern("[^.]+\\.[^.]+\\.[^.]+")))
            .andExpect(jsonPath("$.data.expiresIn").value(3600))
            .andExpect(jsonPath("$.data.refreshExpiresIn").value(1209600))
            .andReturn();

    String newRefreshToken = extractRefreshToken(result);
    ArgumentCaptor<String> tokenHashCaptor = ArgumentCaptor.forClass(String.class);
    verify(activeRefreshTokenRepository)
        .save(eq(user.getId()), tokenHashCaptor.capture(), eq(Duration.ofSeconds(1209600)));
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
    assertThat(tokenHashCaptor.getValue()).isEqualTo(jwtTokenProvider.hashToken(newRefreshToken));
    assertThat(tokenHashCaptor.getValue()).isNotEqualTo(oldRefreshToken);
  }

  @Test
  void 이전_refresh_token을_재사용하면_401로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    String oldRefreshToken = jwtTokenProvider.issueRefreshToken(user);
    String activeRefreshToken = jwtTokenProvider.issueRefreshToken(user);
    when(activeRefreshTokenRepository.findHashByUserId(user.getId()))
        .thenReturn(java.util.Optional.of(jwtTokenProvider.hashToken(activeRefreshToken)));

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
                        .formatted(oldRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVOKED_TOKEN"));
  }

  @Test
  void 새_로그인_후_기존_refresh_token으로_재발급하면_401로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    MvcResult firstLoginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "email": "customer@example.com",
                      "password": "Password123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
    String firstLoginRefreshToken = extractRefreshToken(firstLoginResult);
    reset(activeRefreshTokenRepository);
    MvcResult secondLoginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "email": "customer@example.com",
                      "password": "Password123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
    String secondLoginRefreshToken = extractRefreshToken(secondLoginResult);
    when(activeRefreshTokenRepository.findHashByUserId(user.getId()))
        .thenReturn(java.util.Optional.of(jwtTokenProvider.hashToken(secondLoginRefreshToken)));

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
                        .formatted(firstLoginRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVOKED_TOKEN"));
  }

  @Test
  void refresh_token이_누락되면_400으로_응답한다() throws Exception {
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
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 잘못된_refresh_token이면_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "invalid.jwt"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void 로그인_비밀번호가_틀리면_401로_응답한다() throws Exception {
    userRepository.save(
        new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "customer@example.com",
                      "password": "WrongPassword123!"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_LOGIN_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 일치하지 않습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void 로그인_이메일이_존재하지_않으면_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "unknown@example.com",
                      "password": "Password123!"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_LOGIN_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 일치하지 않습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void 로그인_요청값_검증에_실패하면_400으로_응답한다() throws Exception {
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
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0]", containsString(": ")));
  }

  private String extractRefreshToken(MvcResult result) throws java.io.UnsupportedEncodingException {
    String responseBody = result.getResponse().getContentAsString();
    return responseBody.replaceAll("(?s).*\"refreshToken\":\"([^\"]+)\".*", "$1");
  }
}
