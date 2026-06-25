package com.guingujig.yeolmumarket.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final Duration SUBSCRIPTION_PROBE_INTERVAL = Duration.ofMillis(50);
  private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
  private static final String SERVER_ERROR_DESTINATION = "/queue/errors";

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final SimpMessagingTemplate messagingTemplate;
  private final List<StompSession> sessions = new ArrayList<>();

  @LocalServerPort private int port;

  private WebSocketStompClient stompClient;

  @Autowired
  ChatWebSocketIntegrationTest(
      UserRepository userRepository,
      ProductRepository productRepository,
      ChatRoomRepository chatRoomRepository,
      ChatMessageRepository chatMessageRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      SimpMessagingTemplate messagingTemplate) {
    this.userRepository = userRepository;
    this.productRepository = productRepository;
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
    Product product = productRepository.save(Product.create(seller, "아이패드 미니 6", "생활기스", 450000));
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

  private String issueExpiredAccessToken(User user) {
    return ReflectionTestUtils.invokeMethod(jwtTokenProvider, "issueExpiredAccessToken", user);
  }

  private void deleteAll() {
    chatMessageRepository.deleteAll();
    chatRoomRepository.deleteAll();
    productRepository.deleteAll();
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
}
