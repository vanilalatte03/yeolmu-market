package com.guingujig.yeolmumarket.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.domain.user.dto.UpdateUserRequest;
import com.guingujig.yeolmumarket.domain.user.dto.UpdateUserResponse;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final Long USER_ID = 1L;
  private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 6, 24, 10, 0);

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher eventPublisher;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, passwordEncoder, eventPublisher);
  }

  @Test
  void 닉네임을_변경하면_상품_검색_캐시_무효화_이벤트를_발행한다() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    UpdateUserResponse response =
        userService.updateMe(USER_ID, new UpdateUserRequest("새닉네임", null));

    assertThat(response.nickname()).isEqualTo("새닉네임");
    verify(userRepository).flush();
    verify(eventPublisher).publishEvent(isA(ProductSearchCacheEvictionEvent.class));
  }

  @Test
  void 비밀번호만_변경하면_상품_검색_캐시_무효화_이벤트를_발행하지_않는다() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("NewPassword123!")).thenReturn("encoded-new-password");

    UpdateUserResponse response =
        userService.updateMe(USER_ID, new UpdateUserRequest(null, "NewPassword123!"));

    assertThat(response.nickname()).isEqualTo("열무구매자");
    verify(userRepository).flush();
    verify(eventPublisher, never()).publishEvent(isA(ProductSearchCacheEvictionEvent.class));
  }

  private User user() {
    User user = new User("customer@example.com", "encoded-password", "열무구매자");
    ReflectionTestUtils.setField(user, "id", USER_ID);
    ReflectionTestUtils.setField(user, "modifiedAt", BASE_TIME);
    return user;
  }
}
