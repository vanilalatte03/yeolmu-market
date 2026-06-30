package com.guingujig.yeolmumarket.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessageResponse;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceAsyncFailureTest {

  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChatRoomAuthorizationService chatRoomAuthorizationService;
  @Mock private ChatMessagePersistenceService chatMessagePersistenceService;
  @Mock private ChatMessageSaveFailureNotifier chatMessageSaveFailureNotifier;

  @InjectMocks private ChatRoomService chatRoomService;

  @Test
  void null_메시지는_validation_failed로_실패하고_저장을_위임하지_않는다() {
    assertThatThrownBy(() -> chatRoomService.sendMessage(1L, 10L, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    verifyNoInteractions(
        chatRoomRepository,
        chatRoomAuthorizationService,
        chatMessagePersistenceService,
        chatMessageSaveFailureNotifier);
  }

  @Test
  void 공백_메시지는_validation_failed로_실패하고_저장을_위임하지_않는다() {
    assertThatThrownBy(() -> chatRoomService.sendMessage(1L, 10L, "   "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    verifyNoInteractions(
        chatRoomRepository,
        chatRoomAuthorizationService,
        chatMessagePersistenceService,
        chatMessageSaveFailureNotifier);
  }

  @Test
  void 비동기_저장_작업_등록이_거절되면_접수_ID로_저장_실패를_알린다() {
    ChatMessageResponse response =
        new ChatMessageResponse(
            null,
            "accepted-message-1",
            10L,
            1L,
            "열무구매자",
            "거래 가능할까요?",
            OffsetDateTime.parse("2026-06-22T09:55:00Z"));
    doThrow(new TaskRejectedException("queue full"))
        .when(chatMessagePersistenceService)
        .saveAsync(
            eq(1L), eq(10L), eq("거래 가능할까요?"), any(LocalDateTime.class), eq("accepted-message-1"));

    chatRoomService.saveAcceptedMessageAsync(response);

    verify(chatMessageSaveFailureNotifier).notifyFailure(1L, 10L, "accepted-message-1");
  }
}
