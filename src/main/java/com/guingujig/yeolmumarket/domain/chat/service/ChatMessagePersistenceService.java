package com.guingujig.yeolmumarket.domain.chat.service;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessagePersistenceService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomAuthorizationService chatRoomAuthorizationService;
  private final ChatMessageSaveFailureNotifier chatMessageSaveFailureNotifier;

  /** 발행 경로를 막지 않도록 메시지 저장과 채팅방 정렬 시각 갱신을 별도 트랜잭션에서 처리한다. */
  @Async("chatMessageTaskExecutor")
  @Transactional
  public void saveAsync(
      Long senderId,
      Long roomId,
      String content,
      LocalDateTime acceptedAt,
      String acceptedMessageId) {
    try {
      ChatRoom chatRoom =
          chatRoomRepository
              .findWithParticipantsById(roomId)
              .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
      // 접수와 저장은 별도 트랜잭션이므로 저장 직전의 채팅방 상태로 권한을 다시 확인한다.
      chatRoomAuthorizationService.validateParticipant(chatRoom, senderId);

      User sender = chatRoom.getParticipant(senderId);
      ChatMessage message =
          chatMessageRepository.saveAndFlush(
              ChatMessage.create(chatRoom, sender, content, acceptedAt, acceptedMessageId));
      chatRoomRepository.updateLastMessageAtIfAfter(roomId, message.getCreatedAt());
    } catch (RuntimeException exception) {
      rollbackCurrentTransaction();
      log.warn("비동기 채팅 메시지 저장에 실패했습니다. roomId={}, senderId={}", roomId, senderId, exception);
      notifySaveFailure(senderId, roomId, acceptedMessageId);
    }
  }

  private void rollbackCurrentTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }

  private void notifySaveFailure(Long senderId, Long roomId, String acceptedMessageId) {
    try {
      chatMessageSaveFailureNotifier.notifyFailure(senderId, roomId, acceptedMessageId);
    } catch (RuntimeException exception) {
      log.warn(
          "비동기 채팅 메시지 저장 실패 알림 전송에 실패했습니다. roomId={}, senderId={}, acceptedMessageId={}",
          roomId,
          senderId,
          acceptedMessageId,
          exception);
    }
  }
}
