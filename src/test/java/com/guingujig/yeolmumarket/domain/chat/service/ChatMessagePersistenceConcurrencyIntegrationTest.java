package com.guingujig.yeolmumarket.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.chat.dto.ChatMessageResponse;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 비동기 메시지 저장이 같은 채팅방에 몰려도 유실 없이 저장되는지 검증한다.
 *
 * <p>메시지 INSERT는 FK 검사로 부모 {@code chatroom} 행에 S락을 잡는다. 같은 트랜잭션이 이어서 채팅방 갱신 UPDATE로 X락을 요구하면 동시 저장
 * 시 락 승격 데드락이 발생해 메시지가 유실된다. H2는 이 잠금 특성을 재현하지 못하므로 MySQL 컨테이너로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ChatMessagePersistenceConcurrencyIntegrationTest {

  private static final int SENDERS = 2;
  private static final int MESSAGES_PER_SENDER = 200;
  private static final int EXPECTED_MESSAGES = SENDERS * MESSAGES_PER_SENDER;

  @Container
  static final MySQLContainer mysql =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("yeolmu_market")
          .withUsername("yeolmu")
          .withPassword("local-password");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }

  private final ChatRoomFacade chatRoomFacade;
  private final ChatRoomService chatRoomService;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  ChatMessagePersistenceConcurrencyIntegrationTest(
      ChatRoomFacade chatRoomFacade,
      ChatRoomService chatRoomService,
      ChatRoomRepository chatRoomRepository,
      ChatMessageRepository chatMessageRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.chatRoomFacade = chatRoomFacade;
    this.chatRoomService = chatRoomService;
    this.chatRoomRepository = chatRoomRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Test
  void 같은_채팅방에_양쪽이_동시에_전송해도_모든_메시지가_저장된다() throws Exception {
    chatMessageRepository.deleteAll();
    chatRoomRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    User seller =
        userRepository.save(
            new User("seller@example.com", passwordEncoder.encode("Password123!"), "열무판매자"));
    User buyer =
        userRepository.save(
            new User("buyer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));
    Product product =
        ProductTestFactory.saveProduct(
            productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
    Long roomId = chatRoomFacade.createChatRoom(buyer.getId(), product.getId()).roomId();

    ExecutorService senders = Executors.newFixedThreadPool(SENDERS);
    CountDownLatch ready = new CountDownLatch(SENDERS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(SENDERS);
    long[] senderIds = {buyer.getId(), seller.getId()};

    for (int index = 0; index < SENDERS; index++) {
      long senderId = senderIds[index];
      int senderNo = index;
      senders.submit(
          () -> {
            try {
              ready.countDown();
              start.await();
              for (int sequence = 0; sequence < MESSAGES_PER_SENDER; sequence++) {
                ChatMessageResponse accepted =
                    chatRoomService.sendMessage(senderId, roomId, "s" + senderNo + "-" + sequence);
                chatRoomService.saveAcceptedMessageAsync(accepted);
              }
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }

    ready.await();
    start.countDown();
    assertThat(done.await(2, TimeUnit.MINUTES)).isTrue();
    senders.shutdown();
    assertThat(senders.awaitTermination(1, TimeUnit.MINUTES)).isTrue();

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    while (chatMessageRepository.count() < EXPECTED_MESSAGES && System.nanoTime() < deadline) {
      Thread.sleep(50);
    }

    assertThat(chatMessageRepository.count()).isEqualTo(EXPECTED_MESSAGES);
  }
}
