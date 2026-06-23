package com.guingujig.yeolmumarket.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.chat.dto.CreateChatRoomResponse;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductVisibility;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  ChatRoomServiceTest(
      ChatRoomService chatRoomService,
      ChatRoomRepository chatRoomRepository,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.chatRoomService = chatRoomService;
    this.chatRoomRepository = chatRoomRepository;
    this.productRepository = productRepository;
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
    chatRoomRepository.deleteAll();
    productRepository.deleteAll();
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
    ReflectionTestUtils.setField(product, "status", ProductStatus.DELETED);
    ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.now());
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
    ReflectionTestUtils.setField(product, "visibility", ProductVisibility.HIDDEN);
    productRepository.saveAndFlush(product);

    assertThatThrownBy(() -> chatRoomService.createChatRoom(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  private Product saveProduct(User seller) {
    return productRepository.save(Product.create(seller, "아이패드 미니 6", "생활기스", 450000));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
