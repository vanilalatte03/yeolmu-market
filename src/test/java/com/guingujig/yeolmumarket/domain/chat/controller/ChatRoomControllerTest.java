package com.guingujig.yeolmumarket.domain.chat.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.chat.entity.ChatMessage;
import com.guingujig.yeolmumarket.domain.chat.entity.ChatRoom;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatMessageRepository;
import com.guingujig.yeolmumarket.domain.chat.repository.ChatRoomRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatRoomControllerTest {

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  ChatRoomControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      ChatRoomRepository chatRoomRepository,
      ChatMessageRepository chatMessageRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 인증된_구매자는_상품_채팅방을_생성할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = productRepository.save(Product.create(seller, "아이패드 미니 6", "생활기스", 450000));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/products/{productId}/chat-rooms", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.roomId").isNumber())
        .andExpect(jsonPath("$.data.product.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.product.title").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.buyer.userId").value(buyer.getId()))
        .andExpect(jsonPath("$.data.buyer.nickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.seller.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.seller.nickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 인증되지_않은_사용자가_채팅방을_생성하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(post("/api/products/{productId}/chat-rooms", 1L))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 인증된_사용자는_내_채팅방_목록을_조회할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = productRepository.save(Product.create(seller, "아이패드 미니 6", "생활기스", 450000));
    ChatRoom chatRoom = chatRoomRepository.saveAndFlush(ChatRoom.create(product, buyer, seller));
    chatMessageRepository.saveAndFlush(ChatMessage.create(chatRoom, buyer, "거래 가능할까요?"));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(get("/api/chat-rooms").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].roomId").value(chatRoom.getId()))
        .andExpect(jsonPath("$.data.content[0].productId").value(product.getId()))
        .andExpect(jsonPath("$.data.content[0].productTitle").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.content[0].opponentNickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.content[0].lastMessage").value("거래 가능할까요?"))
        .andExpect(jsonPath("$.data.content[0].lastMessageAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 인증되지_않은_사용자가_내_채팅방_목록을_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/chat-rooms"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 잘못된_페이지로_채팅방_목록을_조회하면_400으로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/chat-rooms")
                .param("page", "-1")
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void 페이지_크기가_최대값을_넘으면_400으로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/chat-rooms")
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
