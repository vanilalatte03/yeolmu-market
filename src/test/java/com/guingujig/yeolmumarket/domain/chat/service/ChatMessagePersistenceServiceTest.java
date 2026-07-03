package com.guingujig.yeolmumarket.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessagePersistenceServiceTest {

  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatRoomAuthorizationService chatRoomAuthorizationService;
  @Mock private ChatMessageSaveFailureNotifier chatMessageSaveFailureNotifier;

  @InjectMocks private ChatMessagePersistenceService chatMessagePersistenceService;

  @Test
  void 메시지를_저장하고_채팅방_정렬_시각을_조건부로_갱신한다() {
    LocalDateTime acceptedAt = LocalDateTime.of(2026, 6, 29, 10, 0);
    User buyer = user(1L, "buyer@example.com", "열무구매자");
    User seller = user(2L, "seller@example.com", "열무판매자");
    ChatRoom chatRoom = chatRoom(10L, buyer, seller);
    ChatMessage savedMessage = ChatMessage.create(chatRoom, buyer, "거래 가능할까요?", acceptedAt);
    when(chatRoomRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(chatRoom));
    when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(savedMessage);

    chatMessagePersistenceService.saveAsync(1L, 10L, "거래 가능할까요?", acceptedAt, "accepted-message-1");

    ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(chatRoomAuthorizationService).validateParticipant(chatRoom, 1L);
    verify(chatMessageRepository).saveAndFlush(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getChatRoom()).isSameAs(chatRoom);
    assertThat(messageCaptor.getValue().getSender()).isSameAs(buyer);
    assertThat(messageCaptor.getValue().getContent()).isEqualTo("거래 가능할까요?");
    assertThat(messageCaptor.getValue().getCreatedAt()).isEqualTo(acceptedAt);
    assertThat(messageCaptor.getValue().getAcceptedMessageId()).isEqualTo("accepted-message-1");
    verify(chatRoomRepository).updateLastMessageAtIfAfter(10L, acceptedAt);
    verifyNoInteractions(chatMessageSaveFailureNotifier);
  }

  @Test
  void 비동기_저장에_실패하면_사용자에게_실패_알림을_보낸다() {
    LocalDateTime acceptedAt = LocalDateTime.of(2026, 6, 29, 10, 0);
    when(chatRoomRepository.findWithParticipantsById(10L)).thenReturn(Optional.empty());

    chatMessagePersistenceService.saveAsync(1L, 10L, "거래 가능할까요?", acceptedAt, "accepted-message-1");

    verify(chatMessageSaveFailureNotifier).notifyFailure(1L, 10L, "accepted-message-1");
    verifyNoInteractions(chatMessageRepository);
  }

  private User user(Long id, String email, String nickname) {
    User user = new User(email, "encoded-password", nickname);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private ChatRoom chatRoom(Long id, User buyer, User seller) {
    Product product = mock(Product.class);
    when(product.getSeller()).thenReturn(seller);
    ChatRoom chatRoom = ChatRoom.create(product, buyer);
    ReflectionTestUtils.setField(chatRoom, "id", id);
    return chatRoom;
  }
}
