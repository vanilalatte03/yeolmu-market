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
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

  private static final int MAX_PAGE_SIZE = 100;

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final ChatRoomAuthorizationService chatRoomAuthorizationService;

  /**
   * 구매자와 상품 판매자 사이의 채팅방을 생성하거나 기존 방을 반환한다.
   *
   * <p>동시 요청으로 유니크 제약 충돌이 발생하면, 이미 생성된 채팅방을 다시 조회해 같은 결과로 수렴시킨다.
   *
   * <p>메서드 전체에 트랜잭션을 두지 않는다. 충돌 후 재조회를 새 트랜잭션(새 스냅샷)으로 수행해야 동시 커밋된 행을 확실히 보기 때문이다. 한 트랜잭션으로 묶으면
   * INSERT 실패가 트랜잭션을 rollback-only로 만들고, MySQL 기본 격리수준(REPEATABLE READ)의 스냅샷 탓에 재조회가 빈 결과를 볼 수 있다.
   */
  public CreateChatRoomResponse createChatRoom(Long buyerId, Long productId) {
    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Product product =
        productRepository
            .findWithSellerById(productId)
            .filter(this::isCreatableChatProduct)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    User seller = product.getSeller();

    if (seller.getId().equals(buyer.getId())) {
      throw new BusinessException(ErrorCode.CANNOT_CHAT_OWN_PRODUCT);
    }

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
   * <p>{@code beforeMessageId}가 있으면 해당 메시지 ID보다 오래된 메시지만 조회한다.
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
    List<ChatMessage> pageMessages = hasNext ? messages.subList(0, size) : messages;
    return ChatMessagesResponse.of(pageMessages, hasNext);
  }

  /**
   * 채팅방 참여자의 메시지를 저장하고, 같은 트랜잭션에서 채팅방의 마지막 대화 시각을 갱신한다.
   *
   * <p>메시지 발행은 트랜잭션 성공 이후 호출자가 수행한다.
   */
  @Transactional
  public ChatMessageResponse sendMessage(Long senderId, Long roomId, String content) {
    ChatRoom chatRoom =
        chatRoomRepository
            .findWithParticipantsByIdForUpdate(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    chatRoomAuthorizationService.validateParticipant(chatRoom, senderId);

    User sender = resolveParticipant(chatRoom, senderId);
    ChatMessage message =
        chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, sender, content));
    chatRoom.updateLastMessageAt(message.getCreatedAt());
    return ChatMessageResponse.from(message);
  }

  private ChatRoom findOrCreateChatRoom(Product product, User buyer, User seller) {
    return chatRoomRepository
        .findByProductAndBuyerAndSeller(product, buyer, seller)
        .orElseGet(() -> saveOrFindExisting(product, buyer, seller));
  }

  private ChatRoom saveOrFindExisting(Product product, User buyer, User seller) {
    try {
      return chatRoomRepository.saveAndFlush(ChatRoom.create(product, buyer, seller));
    } catch (DataIntegrityViolationException exception) {
      return chatRoomRepository
          .findByProductAndBuyerAndSeller(product, buyer, seller)
          .orElseThrow(() -> exception);
    }
  }

  private boolean isCreatableChatProduct(Product product) {
    return product.getDeletedAt() == null
        && product.getStatus() != ProductStatus.DELETED
        && !product.isHidden();
  }

  private User resolveParticipant(ChatRoom chatRoom, Long userId) {
    if (chatRoom.getBuyer().getId().equals(userId)) {
      return chatRoom.getBuyer();
    }
    return chatRoom.getSeller();
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

  private Map<Long, ChatMessage> getLatestMessages(Page<ChatRoom> chatRooms) {
    var chatRoomIds = chatRooms.stream().map(ChatRoom::getId).toList();
    if (chatRoomIds.isEmpty()) {
      return Map.of();
    }
    return chatMessageRepository.findLatestMessagesByChatRoomIds(chatRoomIds).stream()
        .collect(Collectors.toMap(message -> message.getChatRoom().getId(), Function.identity()));
  }
}
