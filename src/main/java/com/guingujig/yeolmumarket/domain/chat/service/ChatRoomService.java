package com.guingujig.yeolmumarket.domain.chat.service;

import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessageResponse;
import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessagesResponse;
import com.guingujig.yeolmumarket.domain.chat.dto.ChatRoomListItemResponse;
import com.guingujig.yeolmumarket.domain.chat.dto.CreateChatRoomResponse;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomAuthorizationService chatRoomAuthorizationService;
  private final ChatMessagePersistenceService chatMessagePersistenceService;
  private final ChatMessageSaveFailureNotifier chatMessageSaveFailureNotifier;
  private final YeolmuProperties yeolmuProperties;

  /**
   * 구매자와 상품 판매자 사이의 채팅방을 생성하거나 기존 방을 반환한다.
   *
   * <p>구매자, 상품, 판매자 조회와 상품 채팅 가능 검증은 호출자가 완료한 뒤 전달한다.
   */
  @Transactional
  public CreateChatRoomResponse findOrCreateChatRoom(User buyer, Product product, User seller) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findByProductAndBuyerAndSeller(product, buyer, seller)
            .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(product, buyer, seller)));
    return CreateChatRoomResponse.from(chatRoom);
  }

  /**
   * 로그인 사용자가 구매자 또는 판매자로 참여한 채팅방 목록을 최신 대화 기준으로 조회한다.
   *
   * <p>메시지가 아직 없는 채팅방도 포함하며, 메시지 요약 필드는 {@code null}로 반환한다.
   */
  @Transactional(readOnly = true)
  public PageResponse<ChatRoomListItemResponse> getMyChatRooms(Long userId, int page, int size) {
    validatePagination(page, size);

    Page<ChatRoom> chatRooms =
        chatRoomRepository.findParticipatingChatRooms(userId, PageRequest.of(page, size));
    Map<Long, ChatMessage> latestMessages = getLatestMessages(chatRooms);
    Page<ChatRoomListItemResponse> response =
        chatRooms.map(
            chatRoom ->
                ChatRoomListItemResponse.of(
                    chatRoom, userId, latestMessages.get(chatRoom.getId())));
    return PageResponse.from(response);
  }

  /**
   * 채팅방 참여자가 저장된 메시지를 최신 메시지부터 커서 방식으로 조회한다.
   *
   * <p>{@code beforeMessageId}가 있으면 해당 메시지의 생성 시각보다 오래된 메시지만 조회한다. 같은 생성 시각에서는 ID를 보조 정렬 기준으로 사용한다.
   */
  @Transactional(readOnly = true)
  public ChatMessagesResponse getPreviousMessages(
      Long userId, Long roomId, Long beforeMessageId, int size) {
    validateMessageCursor(beforeMessageId, size);

    ChatRoom chatRoom =
        chatRoomRepository
            .findWithParticipantsById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    chatRoomAuthorizationService.validateParticipant(chatRoom, userId);

    List<ChatMessage> messages =
        chatMessageRepository.findPreviousMessages(
            chatRoom, beforeMessageId, PageRequest.of(0, size + 1));
    boolean hasNext = messages.size() > size;
    List<ChatMessage> pageMessages = toPageMessages(messages, hasNext, size);
    return ChatMessagesResponse.of(pageMessages, hasNext);
  }

  /**
   * 채팅방 참여자의 메시지 전송 권한을 확인하고, 저장 전 접수 응답을 생성한다.
   *
   * <p>호출자는 이 응답을 먼저 발행한 뒤 {@link #saveAcceptedMessageAsync(ChatMessageResponse)}로 저장을 위임한다.
   */
  @Transactional(readOnly = true)
  public ChatMessageResponse sendMessage(Long senderId, Long roomId, String content) {
    validateMessageContent(content);

    ChatRoom chatRoom =
        chatRoomRepository
            .findWithParticipantsById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    chatRoomAuthorizationService.validateParticipant(chatRoom, senderId);

    User sender = chatRoom.getParticipant(senderId);
    LocalDateTime acceptedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    String acceptedMessageId = UUID.randomUUID().toString();
    return ChatMessageResponse.accepted(acceptedMessageId, roomId, sender, content, acceptedAt);
  }

  public void saveAcceptedMessageAsync(ChatMessageResponse response) {
    try {
      chatMessagePersistenceService.saveAsync(
          response.senderId(),
          response.roomId(),
          response.content(),
          response.createdAt().toLocalDateTime(),
          response.acceptedMessageId());
    } catch (TaskRejectedException exception) {
      log.warn(
          "비동기 채팅 메시지 저장 작업 등록에 실패했습니다. roomId={}, senderId={}, acceptedMessageId={}",
          response.roomId(),
          response.senderId(),
          response.acceptedMessageId(),
          exception);
      notifySaveRegistrationFailure(response);
    }
  }

  private void notifySaveRegistrationFailure(ChatMessageResponse response) {
    try {
      chatMessageSaveFailureNotifier.notifyFailure(
          response.senderId(), response.roomId(), response.acceptedMessageId());
    } catch (RuntimeException exception) {
      log.warn(
          "비동기 채팅 메시지 저장 작업 등록 실패 알림 전송에 실패했습니다. roomId={}, senderId={}, acceptedMessageId={}",
          response.roomId(),
          response.senderId(),
          response.acceptedMessageId(),
          exception);
    }
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size <= 0 || size > yeolmuProperties.pagination().maxPageSize()) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private void validateMessageCursor(Long beforeMessageId, int size) {
    if (size <= 0
        || size > yeolmuProperties.pagination().maxPageSize()
        || (beforeMessageId != null && beforeMessageId <= 0)) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private void validateMessageContent(String content) {
    if (content == null || content.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  private List<ChatMessage> toPageMessages(List<ChatMessage> messages, boolean hasNext, int size) {
    if (hasNext) {
      return messages.subList(0, size);
    }
    return messages;
  }

  private Map<Long, ChatMessage> getLatestMessages(Page<ChatRoom> chatRooms) {
    var chatRoomIds = chatRooms.stream().map(ChatRoom::getId).toList();
    if (chatRoomIds.isEmpty()) {
      return Map.of();
    }
    return chatMessageRepository.findLatestMessagesByChatRoomIds(chatRoomIds).stream()
        .collect(Collectors.toMap(message -> message.getChatRoom().getId(), Function.identity()));
  }
}
