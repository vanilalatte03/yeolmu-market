package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatWebSocketAuthenticationInterceptorTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final ChatWebSocketAuthenticationInterceptor interceptor;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  ChatWebSocketAuthenticationInterceptorTest(
      ChatWebSocketAuthenticationInterceptor interceptor,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.interceptor = interceptor;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 유효한_JWT_CONNECT는_Principal을_설정한다() {
    User user = saveUser("buyer@example.com", "열무구매자");
    Message<?> message = connectMessage("Bearer " + jwtTokenProvider.issueAccessToken(user));

    Message<?> result = interceptor.preSend(message, null);

    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
    assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
    Authentication authentication = (Authentication) accessor.getUser();
    assertThat(authentication.getName()).isEqualTo(user.getId().toString());
    assertThat(authentication.getPrincipal())
        .isInstanceOfSatisfying(
            AuthenticatedUser.class,
            principal -> assertThat(principal.userId()).isEqualTo(user.getId()));
  }

  @Test
  void 토큰이_없는_CONNECT는_UNAUTHORIZED로_실패한다() {
    Message<?> message = connectMessage(null);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOfSatisfying(
            ChatWebSocketAuthenticationException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
  }

  @Test
  void 폐기된_JWT_CONNECT는_REVOKED_TOKEN으로_실패한다() {
    User user = saveUser("buyer@example.com", "열무구매자");
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    when(revokedAccessTokenRepository.exists(jwtTokenProvider.hashToken(accessToken)))
        .thenReturn(true);
    Message<?> message = connectMessage("Bearer " + accessToken);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOfSatisfying(
            ChatWebSocketAuthenticationException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVOKED_TOKEN));
  }

  @Test
  void 폐기_토큰_조회_실패_CONNECT는_REDIS_UNAVAILABLE로_실패한다() {
    User user = saveUser("buyer@example.com", "열무구매자");
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    doThrow(new RedisConnectionFailureException("Redis unavailable"))
        .when(revokedAccessTokenRepository)
        .exists(jwtTokenProvider.hashToken(accessToken));
    Message<?> message = connectMessage("Bearer " + accessToken);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOfSatisfying(
            ChatWebSocketAuthenticationException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE));
  }

  private Message<?> connectMessage(String authorization) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    if (authorization != null) {
      accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
    }
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
