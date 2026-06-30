package com.guingujig.yeolmumarket.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

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
  void 비동기_저장_작업_등록이_거절되면_저장_실패_예외로_전파한다() {
    User buyer = user(1L, "buyer@example.com", "열무구매자");
    User seller = user(2L, "seller@example.com", "열무판매자");
    ChatRoom chatRoom = ChatRoom.create(mock(Product.class), buyer, seller);
    when(chatRoomRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(chatRoom));
    doThrow(new TaskRejectedException("queue full"))
        .when(chatMessagePersistenceService)
        .saveAsync(eq(1L), eq(10L), eq("거래 가능할까요?"), any(LocalDateTime.class), any(String.class));

    assertThatThrownBy(() -> chatRoomService.sendMessage(1L, 10L, "거래 가능할까요?"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_MESSAGE_SAVE_FAILED));

    verify(chatRoomAuthorizationService).validateParticipant(chatRoom, 1L);
    verifyNoInteractions(chatMessageSaveFailureNotifier);
  }

  private User user(Long id, String email, String nickname) {
    User user = new User(email, "encoded-password", nickname);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
