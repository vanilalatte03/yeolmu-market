package com.guingujig.yeolmumarket.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
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
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ChatRoomServiceTest {

  private final ChatRoomService chatRoomService;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  ChatRoomServiceTest(
      ChatRoomService chatRoomService,
      ChatRoomRepository chatRoomRepository,
      ChatMessageRepository chatMessageRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.chatRoomService = chatRoomService;
    this.chatRoomRepository = chatRoomRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
  }

  @AfterEach
  void tearDown() {
    deleteAll();
  }

  private void deleteAll() {
    chatMessageRepository.deleteAll();
    chatRoomRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void 채팅방을_생성하면_구매자와_판매자_상품_요약을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);

    CreateChatRoomResponse response =
        chatRoomService.createChatRoom(buyer.getId(), product.getId());

    assertThat(response.roomId()).isNotNull();
    assertThat(response.product().productId()).isEqualTo(product.getId());
    assertThat(response.product().title()).isEqualTo("아이패드 미니 6");
    assertThat(response.buyer().userId()).isEqualTo(buyer.getId());
    assertThat(response.buyer().nickname()).isEqualTo("열무구매자");
    assertThat(response.seller().userId()).isEqualTo(seller.getId());
    assertThat(response.seller().nickname()).isEqualTo("열무판매자");
    assertThat(response.createdAt()).isNotNull();
    assertThat(chatRoomRepository.count()).isEqualTo(1);
  }

  @Test
  void 이미_채팅방이_있으면_기존_채팅방을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);

    CreateChatRoomResponse first = chatRoomService.createChatRoom(buyer.getId(), product.getId());
    CreateChatRoomResponse second = chatRoomService.createChatRoom(buyer.getId(), product.getId());

    assertThat(second.roomId()).isEqualTo(first.roomId());
    assertThat(chatRoomRepository.count()).isEqualTo(1);
  }

  @Test
  void 동시에_같은_채팅방을_생성해도_하나만_유지한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    int requestCount = 8;
    ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
    CountDownLatch ready = new CountDownLatch(requestCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Long>> tasks = new ArrayList<>();

    for (int i = 0; i < requestCount; i++) {
      tasks.add(
          () -> {
            ready.countDown();
            start.await();
            return chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();
          });
    }

    List<Future<Long>> futures = tasks.stream().map(executorService::submit).toList();
    ready.await();
    start.countDown();

    List<Long> roomIds = new ArrayList<>();
    for (Future<Long> future : futures) {
      roomIds.add(future.get());
    }
    executorService.shutdown();

    assertThat(roomIds).containsOnly(roomIds.getFirst());
    assertThat(chatRoomRepository.count()).isEqualTo(1);
  }

  @Test
  void 판매자가_자신의_상품에_채팅방을_생성하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);

    assertThatThrownBy(() -> chatRoomService.createChatRoom(seller.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANNOT_CHAT_OWN_PRODUCT));
  }

  @Test
  void 존재하지_않는_상품이면_실패한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> chatRoomService.createChatRoom(buyer.getId(), 999L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 삭제된_상품이면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    LocalDateTime deletedAt = LocalDateTime.of(2026, 6, 24, 10, 0);
    ReflectionTestUtils.setField(product, "status", ProductStatus.DELETED);
    ReflectionTestUtils.setField(product, "deletedAt", deletedAt);
    productRepository.saveAndFlush(product);

    assertThatThrownBy(() -> chatRoomService.createChatRoom(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 숨김_상품이면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    ReflectionTestUtils.setField(product, "hidden", true);
    productRepository.saveAndFlush(product);

    assertThatThrownBy(() -> chatRoomService.createChatRoom(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 구매자로_참여한_채팅방_목록을_조회한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    CreateChatRoomResponse createdRoom =
        chatRoomService.createChatRoom(buyer.getId(), product.getId());

    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(buyer.getId(), 0, 10);

    assertThat(response.totalElements()).isEqualTo(1);
    assertThat(response.content()).hasSize(1);
    ChatRoomListItemResponse item = response.content().getFirst();
    assertThat(item.roomId()).isEqualTo(createdRoom.roomId());
    assertThat(item.productId()).isEqualTo(product.getId());
    assertThat(item.productTitle()).isEqualTo("아이패드 미니 6");
    assertThat(item.opponentNickname()).isEqualTo("열무판매자");
  }

  @Test
  void 판매자로_참여한_채팅방_목록을_조회한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    chatRoomService.createChatRoom(buyer.getId(), product.getId());

    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(seller.getId(), 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().opponentNickname()).isEqualTo("열무구매자");
  }

  @Test
  void 참여하지_않은_채팅방은_목록에서_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User otherUser = saveUser("other@example.com", "열무구경꾼");
    Product product = saveProduct(seller);
    chatRoomService.createChatRoom(buyer.getId(), product.getId());

    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(otherUser.getId(), 0, 10);

    assertThat(response.content()).isEmpty();
    assertThat(response.totalElements()).isZero();
  }

  @Test
  void 메시지가_없는_채팅방도_목록에_포함하고_마지막_메시지는_null이다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    chatRoomService.createChatRoom(buyer.getId(), product.getId());

    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(buyer.getId(), 0, 10);

    ChatRoomListItemResponse item = response.content().getFirst();
    assertThat(item.lastMessage()).isNull();
    assertThat(item.lastMessageAt()).isNull();
  }

  @Test
  void 최신_메시지_요약을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();
    ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow();
    chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, buyer, "거래 가능할까요?"));
    chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, seller, "네 가능합니다."));

    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(buyer.getId(), 0, 10);

    ChatRoomListItemResponse item = response.content().getFirst();
    assertThat(item.lastMessage()).isEqualTo("네 가능합니다.");
    assertThat(item.lastMessageAt()).isNotNull();
  }

  @Test
  void 참여자는_이전_메시지를_최신순으로_조회한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();
    ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow();
    chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, buyer, "첫번째 메시지"));
    chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, seller, "두번째 메시지"));

    ChatMessagesResponse response =
        chatRoomService.getPreviousMessages(buyer.getId(), roomId, null, 30);

    assertThat(response.messages()).hasSize(2);
    assertThat(response.messages())
        .extracting(message -> message.content())
        .containsExactly("두번째 메시지", "첫번째 메시지");
    assertThat(response.messages().getFirst().roomId()).isEqualTo(roomId);
    assertThat(response.messages().getFirst().senderId()).isEqualTo(seller.getId());
    assertThat(response.messages().getFirst().senderNickname()).isEqualTo("열무판매자");
    assertThat(response.messages().getFirst().createdAt()).isNotNull();
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  void 커서_이전_메시지를_조회하고_추가_존재_여부를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();
    ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow();
    chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, buyer, "첫번째 메시지"));
    ChatMessage secondMessage =
        chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, seller, "두번째 메시지"));
    ChatMessage thirdMessage =
        chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, buyer, "세번째 메시지"));

    ChatMessagesResponse response =
        chatRoomService.getPreviousMessages(buyer.getId(), roomId, thirdMessage.getId(), 1);

    assertThat(response.messages()).hasSize(1);
    assertThat(response.messages().getFirst().messageId()).isEqualTo(secondMessage.getId());
    assertThat(response.messages().getFirst().content()).isEqualTo("두번째 메시지");
    assertThat(response.hasNext()).isTrue();
  }

  @Test
  void 저장_ID와_접수_시각_순서가_엇갈려도_이전_메시지는_접수_시각_기준으로_조회한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();
    ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow();
    LocalDateTime baseTime = LocalDateTime.of(2026, 6, 29, 10, 0);
    ChatMessage newerMessage =
        chatMessageRepository.saveAndFlush(
            ChatMessage.create(chatRoom, seller, "나중에 접수된 메시지", baseTime.plusSeconds(1)));
    ChatMessage olderMessage =
        chatMessageRepository.saveAndFlush(
            ChatMessage.create(chatRoom, buyer, "먼저 접수된 메시지", baseTime));

    ChatMessagesResponse firstPage =
        chatRoomService.getPreviousMessages(buyer.getId(), roomId, null, 1);

    assertThat(firstPage.messages()).hasSize(1);
    assertThat(firstPage.messages().getFirst().messageId()).isEqualTo(newerMessage.getId());
    assertThat(firstPage.hasNext()).isTrue();

    ChatMessagesResponse secondPage =
        chatRoomService.getPreviousMessages(buyer.getId(), roomId, newerMessage.getId(), 1);

    assertThat(secondPage.messages()).hasSize(1);
    assertThat(secondPage.messages().getFirst().messageId()).isEqualTo(olderMessage.getId());
    assertThat(secondPage.hasNext()).isFalse();
  }

  @Test
  void 메시지가_없는_채팅방은_빈_목록을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();

    ChatMessagesResponse response =
        chatRoomService.getPreviousMessages(seller.getId(), roomId, null, 30);

    assertThat(response.messages()).isEmpty();
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  void 메시지_전송은_저장을_비동기로_처리하고_접수_응답을_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();

    var response = chatRoomService.sendMessage(buyer.getId(), roomId, "거래 가능할까요?");

    assertThat(response.messageId()).isNull();
    assertThat(response.acceptedMessageId()).isNotBlank();
    assertThat(response.roomId()).isEqualTo(roomId);
    assertThat(response.senderId()).isEqualTo(buyer.getId());
    assertThat(response.senderNickname()).isEqualTo("열무구매자");
    assertThat(response.content()).isEqualTo("거래 가능할까요?");

    List<ChatMessage> savedMessages = waitForSavedMessages(1);
    assertThat(savedMessages).hasSize(1);
    ChatMessage savedMessage = savedMessages.getFirst();
    assertThat(savedMessage.getChatRoom().getId()).isEqualTo(roomId);
    assertThat(savedMessage.getSender().getId()).isEqualTo(buyer.getId());
    assertThat(savedMessage.getContent()).isEqualTo("거래 가능할까요?");
    assertThat(savedMessage.getCreatedAt()).isEqualTo(response.createdAt().toLocalDateTime());
    assertThat(savedMessage.getAcceptedMessageId()).isEqualTo(response.acceptedMessageId());

    ChatRoom updatedRoom = chatRoomRepository.findById(roomId).orElseThrow();
    assertThat(updatedRoom.getLastMessageAt()).isEqualTo(savedMessage.getCreatedAt());

    ChatMessagesResponse history =
        chatRoomService.getPreviousMessages(buyer.getId(), roomId, null, 30);
    assertThat(history.messages().getFirst().messageId()).isEqualTo(savedMessage.getId());
    assertThat(history.messages().getFirst().acceptedMessageId())
        .isEqualTo(response.acceptedMessageId());
  }

  @Test
  void 없는_채팅방의_이전_메시지를_조회하면_실패한다() {
    User user = saveUser("user@example.com", "열무유저");

    assertThatThrownBy(() -> chatRoomService.getPreviousMessages(user.getId(), 999L, null, 30))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
  }

  @Test
  void 참여자가_아니면_이전_메시지_조회에_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User otherUser = saveUser("other@example.com", "열무구경꾼");
    Product product = saveProduct(seller);
    Long roomId = chatRoomService.createChatRoom(buyer.getId(), product.getId()).roomId();

    assertThatThrownBy(
            () -> chatRoomService.getPreviousMessages(otherUser.getId(), roomId, null, 30))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
  }

  @Test
  void 잘못된_메시지_조회조건이면_실패한다() {
    User user = saveUser("user@example.com", "열무유저");

    assertThatThrownBy(() -> chatRoomService.getPreviousMessages(user.getId(), 1L, null, 0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
    assertThatThrownBy(() -> chatRoomService.getPreviousMessages(user.getId(), 1L, null, 101))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
    assertThatThrownBy(() -> chatRoomService.getPreviousMessages(user.getId(), 1L, 0L, 30))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  @Test
  void 채팅방_목록은_마지막_메시지_시각_기준으로_정렬한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User firstSeller = saveUser("first-seller@example.com", "첫번째판매자");
    User secondSeller = saveUser("second-seller@example.com", "두번째판매자");
    Product firstProduct = saveProduct(firstSeller);
    Product secondProduct = saveProduct(secondSeller);
    Long firstRoomId = chatRoomService.createChatRoom(buyer.getId(), firstProduct.getId()).roomId();
    Long secondRoomId =
        chatRoomService.createChatRoom(buyer.getId(), secondProduct.getId()).roomId();
    ChatRoom firstRoom = chatRoomRepository.findById(firstRoomId).orElseThrow();
    ChatRoom secondRoom = chatRoomRepository.findById(secondRoomId).orElseThrow();
    LocalDateTime baseTime = LocalDateTime.of(2026, 6, 24, 10, 0);
    ReflectionTestUtils.setField(firstRoom, "lastMessageAt", baseTime.minusMinutes(10));
    ReflectionTestUtils.setField(secondRoom, "lastMessageAt", baseTime);
    chatRoomRepository.saveAllAndFlush(List.of(firstRoom, secondRoom));

    PageResponse<ChatRoomListItemResponse> response =
        chatRoomService.getMyChatRooms(buyer.getId(), 0, 10);

    assertThat(response.content())
        .extracting(ChatRoomListItemResponse::roomId)
        .containsExactly(secondRoomId, firstRoomId);
  }

  @Test
  void 잘못된_페이지_요청이면_실패한다() {
    User user = saveUser("user@example.com", "열무유저");

    assertThatThrownBy(() -> chatRoomService.getMyChatRooms(user.getId(), -1, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  @Test
  void 페이지_크기가_최대값을_넘으면_실패한다() {
    User user = saveUser("user@example.com", "열무유저");

    assertThatThrownBy(() -> chatRoomService.getMyChatRooms(user.getId(), 0, 101))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  private Product saveProduct(User seller) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private List<ChatMessage> waitForSavedMessages(int expectedSize) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    List<ChatMessage> messages = chatMessageRepository.findAll();
    while (messages.size() != expectedSize && System.nanoTime() < deadline) {
      Thread.sleep(50);
      messages = chatMessageRepository.findAll();
    }
    return messages;
  }
}
