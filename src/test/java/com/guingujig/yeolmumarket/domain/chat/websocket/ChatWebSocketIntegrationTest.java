package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;
  @MockitoSpyBean private ChatMessageRepository chatMessageRepository;

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final Duration SUBSCRIPTION_PROBE_INTERVAL = Duration.ofMillis(50);
  private static final Instant EXPIRED_TOKEN_ISSUED_AT = Instant.EPOCH;
  private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
  private static final String SERVER_ERROR_DESTINATION = "/queue/errors";

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;
  private final String jwtSecret;
  private final List<StompSession> sessions = new ArrayList<>();

  @LocalServerPort private int port;

  private WebSocketStompClient stompClient;

  @Autowired
  ChatWebSocketIntegrationTest(
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      ChatRoomRepository chatRoomRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      SimpMessagingTemplate messagingTemplate,
      ObjectMapper objectMapper,
      @Value("${jwt.secret}") String jwtSecret) {
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
    this.jwtSecret = jwtSecret;
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
  void 유효한_JWT_CONNECT는_성공하고_user_error_queue를_구독할_수_있다() throws Exception {
    User user = saveUser("buyer@example.com", "열무구매자");

    StompSession session = connectWithToken(user);

    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, user, errorHandler);

    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 토큰이_없는_CONNECT는_ERROR_frame으로_실패한다() throws Exception {
    String payload = connectExpectingError(new StompHeaders());

    assertThat(payload).contains("\"code\":\"UNAUTHORIZED\"");
  }

  @Test
  void 잘못된_토큰_CONNECT는_ERROR_frame으로_실패한다() throws Exception {
    StompHeaders headers = new StompHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt");

    String payload = connectExpectingError(headers);

    assertThat(payload).contains("\"code\":\"INVALID_TOKEN\"");
  }

  @Test
  void 만료된_토큰_CONNECT는_ERROR_frame으로_실패한다() throws Exception {
    User user = saveUser("buyer@example.com", "열무구매자");
    StompHeaders headers = new StompHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + issueExpiredAccessToken(user));

    String payload = connectExpectingError(headers);

    assertThat(payload).contains("\"code\":\"EXPIRED_TOKEN\"");
  }

  @Test
  void 폐기된_토큰_CONNECT는_ERROR_frame으로_실패한다() throws Exception {
    User user = saveUser("buyer@example.com", "열무구매자");
    String accessToken = jwtTokenProvider.issueAccessToken(user);
    when(revokedAccessTokenRepository.exists(jwtTokenProvider.hashToken(accessToken)))
        .thenReturn(true);
    StompHeaders headers = new StompHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

    String payload = connectExpectingError(headers);

    assertThat(payload).contains("\"code\":\"REVOKED_TOKEN\"");
  }

  @Test
  void 참여자는_채팅방을_구독할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(buyer);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, buyer, errorHandler);

    session.subscribe("/sub/chat-rooms/" + chatRoom.getId(), new NoopFrameHandler());

    assertThat(session.isConnected()).isTrue();
    assertThat(errorHandler.pollPayload()).isNull();
  }

  @Test
  void 참여자는_채팅방_저장_실패_보정_destination을_구독할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(buyer);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, buyer, errorHandler);

    session.subscribe("/sub/chat-rooms/" + chatRoom.getId() + "/errors", new NoopFrameHandler());

    assertThat(session.isConnected()).isTrue();
    assertThat(errorHandler.pollPayload()).isNull();
  }

  @Test
  void 없는_채팅방_SUBSCRIBE는_user_error_queue로_실패하고_연결을_유지한다() throws Exception {
    User user = saveUser("buyer@example.com", "열무구매자");
    StompSession session = connectWithToken(user);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, user, errorHandler);

    session.subscribe("/sub/chat-rooms/999", new NoopFrameHandler());

    String payload = errorHandler.pollPayload();
    assertThat(payload).contains("\"code\":\"CHAT_ROOM_NOT_FOUND\"");
    assertThat(payload).contains("\"roomId\":999");
    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 참여자가_아닌_SUBSCRIBE는_user_error_queue로_실패하고_연결을_유지한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User otherUser = saveUser("other@example.com", "열무구경꾼");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(otherUser);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, otherUser, errorHandler);

    session.subscribe("/sub/chat-rooms/" + chatRoom.getId(), new NoopFrameHandler());

    String payload = errorHandler.pollPayload();
    assertThat(payload).contains("\"code\":\"CHAT_ROOM_ACCESS_DENIED\"");
    assertThat(payload).contains("\"roomId\":" + chatRoom.getId());
    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 참여자가_아닌_저장_실패_보정_SUBSCRIBE는_user_error_queue로_실패한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User otherUser = saveUser("other@example.com", "열무구경꾼");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(otherUser);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, otherUser, errorHandler);

    session.subscribe("/sub/chat-rooms/" + chatRoom.getId() + "/errors", new NoopFrameHandler());

    String payload = errorHandler.pollPayload();
    assertThat(payload).contains("\"code\":\"CHAT_ROOM_ACCESS_DENIED\"");
    assertThat(payload).contains("\"roomId\":" + chatRoom.getId());
    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 참여자는_SEND로_메시지를_저장하고_구독자에게_발행한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(buyer);
    MessageFrameHandler messageHandler = new MessageFrameHandler();
    subscribeToChatRoom(session, chatRoom.getId(), messageHandler);

    session.send("/pub/chat-rooms/" + chatRoom.getId() + "/message", "{\"content\":\"거래 가능할까요?\"}");

    String payload = messageHandler.pollPayload();
    assertThat(payload).contains("\"messageId\":null");
    assertThat(payload).contains("\"acceptedMessageId\":");
    assertThat(payload).contains("\"roomId\":" + chatRoom.getId());
    assertThat(payload).contains("\"senderId\":" + buyer.getId());
    assertThat(payload).contains("\"senderNickname\":\"열무구매자\"");
    assertThat(payload).contains("\"content\":\"거래 가능할까요?\"");
    assertThat(payload).contains("\"createdAt\":");
    List<ChatMessage> savedMessages = waitForSavedMessages(1);
    assertThat(savedMessages).hasSize(1);
    ChatMessage savedMessage = savedMessages.getFirst();
    assertThat(savedMessage.getChatRoom().getId()).isEqualTo(chatRoom.getId());
    assertThat(savedMessage.getSender().getId()).isEqualTo(buyer.getId());
    assertThat(savedMessage.getContent()).isEqualTo("거래 가능할까요?");
    assertThat(savedMessage.getAcceptedMessageId()).isNotBlank();
    assertThat(payload)
        .contains("\"acceptedMessageId\":\"" + savedMessage.getAcceptedMessageId() + "\"");
    ChatRoom updatedRoom = chatRoomRepository.findById(chatRoom.getId()).orElseThrow();
    assertThat(updatedRoom.getLastMessageAt()).isEqualTo(savedMessage.getCreatedAt());
  }

  @Test
  void 발행_이후_비동기_저장에_실패하면_방_보정_destination과_user_error_queue로_알린다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    doThrow(new DataIntegrityViolationException("save failed"))
        .when(chatMessageRepository)
        .saveAndFlush(any(ChatMessage.class));
    StompSession buyerSession = connectWithToken(buyer);
    StompSession sellerSession = connectWithToken(seller);
    MessageFrameHandler messageHandler = new MessageFrameHandler();
    MessageFrameHandler roomErrorHandler = new MessageFrameHandler();
    ErrorQueueFrameHandler userErrorHandler = new ErrorQueueFrameHandler();
    subscribeToChatRoom(buyerSession, chatRoom.getId(), messageHandler);
    subscribeToErrorQueue(buyerSession, buyer, userErrorHandler);
    String roomErrorDestination = "/sub/chat-rooms/" + chatRoom.getId() + "/errors";
    sellerSession.subscribe(roomErrorDestination, roomErrorHandler);
    waitForChatRoomSubscriptionReady(roomErrorDestination, roomErrorHandler);

    buyerSession.send(
        "/pub/chat-rooms/" + chatRoom.getId() + "/message", "{\"content\":\"거래 가능할까요?\"}");

    String messagePayload = messageHandler.pollPayload();
    assertThat(messagePayload).contains("\"messageId\":null");
    String acceptedMessageId =
        readPayload(messagePayload).requiredAt("/acceptedMessageId").asString();
    String expectedAcceptedMessageId = "\"acceptedMessageId\":\"" + acceptedMessageId + "\"";
    String expectedRoomId = "\"roomId\":" + chatRoom.getId();

    String roomErrorPayload = roomErrorHandler.pollPayload();
    assertThat(roomErrorPayload)
        .contains("\"code\":\"CHAT_MESSAGE_SAVE_FAILED\"")
        .contains(expectedRoomId)
        .contains(expectedAcceptedMessageId);

    String userErrorPayload = userErrorHandler.pollPayload();
    assertThat(userErrorPayload)
        .contains("\"code\":\"CHAT_MESSAGE_SAVE_FAILED\"")
        .contains(expectedRoomId)
        .contains(expectedAcceptedMessageId);
    assertThat(chatMessageRepository.findAll()).isEmpty();
    assertThat(buyerSession.isConnected()).isTrue();
    assertThat(sellerSession.isConnected()).isTrue();
  }

  @Test
  void 빈_메시지_SEND는_user_error_queue로_실패하고_저장_발행하지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(buyer);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    MessageFrameHandler messageHandler = new MessageFrameHandler();
    subscribeToErrorQueue(session, buyer, errorHandler);
    subscribeToChatRoom(session, chatRoom.getId(), messageHandler);

    session.send("/pub/chat-rooms/" + chatRoom.getId() + "/message", "{\"content\":\"   \"}");

    String errorPayload = errorHandler.pollPayload();
    assertThat(errorPayload).contains("\"code\":\"VALIDATION_FAILED\"");
    assertThat(errorPayload).contains("\"roomId\":" + chatRoom.getId());
    assertThat(messageHandler.pollPayload(SUBSCRIPTION_PROBE_INTERVAL)).isNull();
    assertThat(chatMessageRepository.findAll()).isEmpty();
    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 빈_body_SEND는_user_error_queue로_실패하고_저장_발행하지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession session = connectWithToken(buyer);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    MessageFrameHandler messageHandler = new MessageFrameHandler();
    subscribeToErrorQueue(session, buyer, errorHandler);
    subscribeToChatRoom(session, chatRoom.getId(), messageHandler);

    session.send("/pub/chat-rooms/" + chatRoom.getId() + "/message", "");

    String errorPayload = errorHandler.pollPayload();
    assertThat(errorPayload).contains("\"code\":\"VALIDATION_FAILED\"");
    assertThat(errorPayload).contains("\"roomId\":" + chatRoom.getId());
    assertThat(messageHandler.pollPayload(SUBSCRIPTION_PROBE_INTERVAL)).isNull();
    assertThat(chatMessageRepository.findAll()).isEmpty();
    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 없는_채팅방_SEND는_user_error_queue로_실패하고_연결을_유지한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    StompSession session = connectWithToken(buyer);
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToErrorQueue(session, buyer, errorHandler);

    session.send("/pub/chat-rooms/999/message", "{\"content\":\"거래 가능할까요?\"}");

    String errorPayload = errorHandler.pollPayload();
    assertThat(errorPayload).contains("\"code\":\"CHAT_ROOM_NOT_FOUND\"");
    assertThat(errorPayload).contains("\"roomId\":999");
    assertThat(chatMessageRepository.findAll()).isEmpty();
    assertThat(session.isConnected()).isTrue();
  }

  @Test
  void 참여자가_아닌_SEND는_user_error_queue로_실패하고_저장_발행하지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User otherUser = saveUser("other@example.com", "열무구경꾼");
    ChatRoom chatRoom = saveChatRoom(seller, buyer);
    StompSession buyerSession = connectWithToken(buyer);
    StompSession otherSession = connectWithToken(otherUser);
    MessageFrameHandler messageHandler = new MessageFrameHandler();
    ErrorQueueFrameHandler errorHandler = new ErrorQueueFrameHandler();
    subscribeToChatRoom(buyerSession, chatRoom.getId(), messageHandler);
    subscribeToErrorQueue(otherSession, otherUser, errorHandler);

    otherSession.send(
        "/pub/chat-rooms/" + chatRoom.getId() + "/message", "{\"content\":\"거래 가능할까요?\"}");

    String errorPayload = errorHandler.pollPayload();
    assertThat(errorPayload).contains("\"code\":\"CHAT_ROOM_ACCESS_DENIED\"");
    assertThat(errorPayload).contains("\"roomId\":" + chatRoom.getId());
    assertThat(messageHandler.pollPayload(SUBSCRIPTION_PROBE_INTERVAL)).isNull();
    assertThat(chatMessageRepository.findAll()).isEmpty();
    assertThat(otherSession.isConnected()).isTrue();
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

  private String connectExpectingError(StompHeaders headers) throws Exception {
    ErrorFrameHandler handler = new ErrorFrameHandler();
    CompletableFuture<StompSession> sessionFuture =
        stompClient.connectAsync(wsUrl(), new WebSocketHttpHeaders(), headers, handler);
    try {
      StompSession session = sessionFuture.get(1, TimeUnit.SECONDS);
      sessions.add(session);
    } catch (Exception ignored) {
      // CONNECT 실패 시 future가 ERROR frame 수신 뒤 예외로 종료될 수 있다.
    }
    String payload = handler.pollPayload();
    assertThat(payload).isNotNull();
    return payload;
  }

  private String wsUrl() {
    return "ws://localhost:" + port + "/ws";
  }

  private ChatRoom saveChatRoom(User seller, User buyer) {
    Product product =
        ProductTestFactory.saveProduct(
            productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
    return chatRoomRepository.saveAndFlush(ChatRoom.create(product, buyer));
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

  private List<ChatMessage> waitForSavedMessages(int expectedSize) throws InterruptedException {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    List<ChatMessage> messages = chatMessageRepository.findAll();
    while (messages.size() != expectedSize && System.nanoTime() < deadline) {
      Thread.sleep(SUBSCRIPTION_PROBE_INTERVAL.toMillis());
      messages = chatMessageRepository.findAll();
    }
    return messages;
  }

  private JsonNode readPayload(String payload) throws Exception {
    return objectMapper.readTree(payload);
  }

  private String issueExpiredAccessToken(User user) {
    return jwtTokenProviderAt(EXPIRED_TOKEN_ISSUED_AT).issueAccessToken(user);
  }

  private JwtTokenProvider jwtTokenProviderAt(Instant instant) {
    return new JwtTokenProvider(
        objectMapper,
        Clock.fixed(instant, ZoneOffset.UTC),
        jwtSecret,
        jwtTokenProvider.getAccessTokenValiditySeconds(),
        jwtTokenProvider.getRefreshTokenValiditySeconds());
  }

  private void deleteAll() {
    chatMessageRepository.deleteAll();
    chatRoomRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private static class ErrorFrameHandler extends StompSessionHandlerAdapter {

    private final BlockingQueue<String> payloads = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      payloads.add(new String((byte[]) payload, StandardCharsets.UTF_8));
    }

    @Override
    public void handleException(
        StompSession session,
        StompCommand command,
        StompHeaders headers,
        byte[] payload,
        Throwable exception) {
      payloads.add(new String(payload, StandardCharsets.UTF_8));
    }

    String pollPayload() throws InterruptedException {
      return pollPayload(TIMEOUT);
    }

    String pollPayload(Duration timeout) throws InterruptedException {
      return payloads.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
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

  private static class NoopFrameHandler implements StompFrameHandler {

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {}
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

    String pollPayload() throws InterruptedException {
      return pollPayload(TIMEOUT);
    }

    String pollPayload(Duration timeout) throws InterruptedException {
      return payloads.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
  }
}
