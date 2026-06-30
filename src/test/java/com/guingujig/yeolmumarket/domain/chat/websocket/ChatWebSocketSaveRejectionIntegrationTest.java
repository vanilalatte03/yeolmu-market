package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.chat.service.ChatMessagePersistenceService;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketSaveRejectionIntegrationTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;
  @MockitoBean private ChatMessagePersistenceService chatMessagePersistenceService;

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final Duration SUBSCRIPTION_PROBE_INTERVAL = Duration.ofMillis(50);
  private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
  private static final String SERVER_ERROR_DESTINATION = "/queue/errors";

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final SimpMessagingTemplate messagingTemplate;
  private final List<StompSession> sessions = new ArrayList<>();

  @LocalServerPort private int port;

  private WebSocketStompClient stompClient;

  @Autowired
  ChatWebSocketSaveRejectionIntegrationTest(
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      ChatRoomRepository chatRoomRepository,
      ChatMessageRepository chatMessageRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      SimpMessagingTemplate messagingTemplate) {
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.messagingTemplate = messagingTemplate;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(
        new CompositeMessageConverter(
            List.of(new StringMessageConverter(), new ByteArrayMessageConverter())));
  }

  @AfterEach
  void tearDown() {
    sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    sessions.clear();
    if (stompClient != null) {
      stompClient.stop();
    }
    deleteAll();
  }

  @Test
  void 저장_작업_등록이_거절되면_두_구독자에게_메시지를_발행하지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    doThrow(new TaskRejectedException("queue full"))
        .when(chatMessagePersistenceService)
        .saveAsync(
            eq(buyer.getId()),
            eq(chatRoom.getId()),
            eq("거래 가능할까요?"),
            any(LocalDateTime.class),
            any(String.class));

    StompSession buyerSession = connectWithToken(buyer);
    StompSession sellerSession = connectWithToken(seller);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    MessageFrameHandler buyerMessageHandler = new MessageFrameHandler();
    MessageFrameHandler sellerMessageHandler = new MessageFrameHandler();
    subscribeToErrorQueue(buyerSession, buyer, errorHandler);
    subscribeToChatRoom(buyerSession, chatRoom.getId(), buyerMessageHandler);
    subscribeToChatRoom(sellerSession, chatRoom.getId(), sellerMessageHandler);
    drainSubscriptionProbePayloads(buyerMessageHandler, sellerMessageHandler);

    buyerSession.send(
        "/pub/chat-rooms/" + chatRoom.getId() + "/message", "{\"content\":\"거래 가능할까요?\"}");

    String errorPayload = errorHandler.pollPayload();
    assertThat(errorPayload).contains("\"code\":\"CHAT_MESSAGE_SAVE_FAILED\"");
    assertThat(errorPayload).contains("\"roomId\":" + chatRoom.getId());
    assertNoMessagePayload(buyerMessageHandler);
    assertNoMessagePayload(sellerMessageHandler);
    assertThat(chatMessageRepository.findAll()).isEmpty();
    assertThat(buyerSession.isConnected()).isTrue();
    assertThat(sellerSession.isConnected()).isTrue();
  }

  private StompSession connectWithToken(User user) throws Exception {
    StompHeaders headers = new StompHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(user));
    StompSession session =
        stompClient
            .connectAsync(
                wsUrl(), new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {})
            .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    sessions.add(session);
    return session;
  }

  private String wsUrl() {
    return "ws://localhost:" + port + "/ws";
  }

  private ChatRoom saveChatRoom(User seller, User buyer) {
    Product product =
        ProductTestFactory.saveProduct(
            productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
    return chatRoomRepository.saveAndFlush(ChatRoom.create(product, buyer, seller));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private void subscribeToErrorQueue(
      StompSession session, User user, ErrorQueueFrameHandler errorHandler)
      throws InterruptedException {
    session.subscribe(USER_ERROR_DESTINATION, errorHandler);
    waitForErrorQueueReady(user, errorHandler);
  }

  private void waitForErrorQueueReady(User user, ErrorQueueFrameHandler errorHandler)
      throws InterruptedException {
    String probePayload = "__subscription_probe__:" + UUID.randomUUID();
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      messagingTemplate.convertAndSendToUser(
          user.getId().toString(), SERVER_ERROR_DESTINATION, probePayload);
      String payload = errorHandler.pollPayload(SUBSCRIPTION_PROBE_INTERVAL);
      if (probePayload.equals(payload)) {
        return;
      }
      assertThat(payload)
          .as("unexpected user error queue payload during subscription probe")
          .isNull();
    }
    assertThat(false).as("user error queue subscription should receive probe").isTrue();
  }

  private void subscribeToChatRoom(
      StompSession session, Long roomId, MessageFrameHandler messageHandler)
      throws InterruptedException {
    String destination = "/sub/chat-rooms/" + roomId;
    session.subscribe(destination, messageHandler);
    waitForChatRoomSubscriptionReady(destination, messageHandler);
  }

  private void waitForChatRoomSubscriptionReady(
      String destination, MessageFrameHandler messageHandler) throws InterruptedException {
    String probePayload = "__subscription_probe__:" + UUID.randomUUID();
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      messagingTemplate.convertAndSend(destination, probePayload);
      String payload = messageHandler.pollPayload(SUBSCRIPTION_PROBE_INTERVAL);
      if (probePayload.equals(payload)) {
        return;
      }
      assertThat(payload)
          .as("unexpected chat room subscription payload during subscription probe")
          .isNull();
    }
    assertThat(false).as("chat room subscription should receive probe").isTrue();
  }

  private void drainSubscriptionProbePayloads(MessageFrameHandler... handlers)
      throws InterruptedException {
    for (MessageFrameHandler handler : handlers) {
      String payload = handler.pollPayload(SUBSCRIPTION_PROBE_INTERVAL);
      while (payload != null) {
        assertThat(payload).startsWith("__subscription_probe__:");
        payload = handler.pollPayload(Duration.ZERO);
      }
    }
  }

  private void assertNoMessagePayload(MessageFrameHandler handler) throws InterruptedException {
    long deadline = System.nanoTime() + SUBSCRIPTION_PROBE_INTERVAL.toNanos();
    while (System.nanoTime() < deadline) {
      String payload = handler.pollPayload(Duration.ofNanos(deadline - System.nanoTime()));
      if (payload == null) {
        return;
      }
      assertThat(payload).startsWith("__subscription_probe__:");
    }
  }

  private void deleteAll() {
    chatMessageRepository.deleteAll();
    chatRoomRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private static class ErrorQueueFrameHandler implements StompFrameHandler {

    private final BlockingQueue<String> payloads = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      payloads.add((String) payload);
    }

    String pollPayload() throws InterruptedException {
      return pollPayload(TIMEOUT);
    }

    String pollPayload(Duration timeout) throws InterruptedException {
      return payloads.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  private static class MessageFrameHandler implements StompFrameHandler {

    private final BlockingQueue<String> payloads = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      payloads.add((String) payload);
    }

    String pollPayload(Duration timeout) throws InterruptedException {
      return payloads.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
  }
}
