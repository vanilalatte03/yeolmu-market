package com.guingujig.yeolmumarket.domain.chat.service;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomAuthorizationService {

  private final ChatRoomRepository chatRoomRepository;

  /**
   * 채팅방이 존재하고 사용자가 구매자 또는 판매자로 참여 중인지 검증한다.
   *
   * <p>STOMP SUBSCRIBE와 이후 SEND 권한 검증이 같은 참여자 판정을 재사용하기 위한 공통 경계다.
   */
  @Transactional(readOnly = true)
  public void validateParticipant(Long roomId, Long userId) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findWithParticipantsById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    validateParticipant(chatRoom, userId);
  }

  /**
   * 이미 참여자 정보가 로딩된 채팅방에서 사용자 참여 여부를 검증한다.
   *
   * <p>참여자가 아니면 {@code CHAT_ROOM_ACCESS_DENIED} 예외를 던진다.
   */
  public void validateParticipant(ChatRoom chatRoom, Long userId) {
    if (!chatRoom.isParticipant(userId)) {
      throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }
  }
}
