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
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
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

  private static final int MAX_PAGE_SIZE = 100;

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final ChatRoomAuthorizationService chatRoomAuthorizationService;
  private final ChatMessagePersistenceService chatMessagePersistenceService;

  /**
   * 구매자와 상품 판매자 사이의 채팅방을 생성하거나 기존 방을 반환한다.
   *
   * <p>상품 row lock으로 같은 상품의 채팅방 생성 요청을 직렬화해, 기존 방 조회와 신규 생성의 책임을 명확히 나눈다.
   */
  @Transactional
  public CreateChatRoomResponse createChatRoom(Long buyerId, Long productId) {
    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Product product =
        productRepository
            .findWithSellerByIdForUpdate(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    product.validateChatCreatableBy(buyerId);
    User seller = product.getSeller();

    return CreateChatRoomResponse.from(findOrCreateChatRoom(product, buyer, seller));
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
   * 채팅방 참여자의 메시지 전송 권한을 확인하고, DB 저장은 비동기로 위임한다.
   *
   * <p>저장 작업 등록에 성공하면 호출자가 저장 완료를 기다리지 않고 메시지를 즉시 발행한다.
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
    try {
      chatMessagePersistenceService.saveAsync(
          senderId, roomId, content, acceptedAt, acceptedMessageId);
    } catch (TaskRejectedException exception) {
      log.warn(
          "비동기 채팅 메시지 저장 작업 등록에 실패했습니다. roomId={}, senderId={}, acceptedMessageId={}",
          roomId,
          senderId,
          acceptedMessageId,
          exception);
      throw new BusinessException(ErrorCode.CHAT_MESSAGE_SAVE_FAILED);
    }
    return ChatMessageResponse.accepted(acceptedMessageId, roomId, sender, content, acceptedAt);
  }

  private ChatRoom findOrCreateChatRoom(Product product, User buyer, User seller) {
    return chatRoomRepository
        .findByProductAndBuyerAndSeller(product, buyer, seller)
        .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(product, buyer, seller)));
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private void validateMessageCursor(Long beforeMessageId, int size) {
    if (size <= 0 || size > MAX_PAGE_SIZE || (beforeMessageId != null && beforeMessageId <= 0)) {
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
