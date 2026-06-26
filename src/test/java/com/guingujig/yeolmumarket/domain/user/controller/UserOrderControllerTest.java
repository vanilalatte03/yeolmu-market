package com.guingujig.yeolmumarket.domain.user.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserOrderControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  UserOrderControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 구매자가_내_구매_주문_목록_조회하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(get("/api/users/me/orders").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].productTitle").value("아이패드 미니 6세대"))
        .andExpect(jsonPath("$.data.content[0].price").value(430000))
        .andExpect(jsonPath("$.data.content[0].sellerNickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 구매_주문_목록은_다른_사용자의_주문을_포함하지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product1 = saveProduct(seller, "내상품", 100000);
    Product product2 = saveProduct(seller, "타인상품", 200000);
    saveOrder(buyer, product1);
    saveOrder(other, product2);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(get("/api/users/me/orders").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].productTitle").value("내상품"));
  }

  @Test
  void 구매_주문_목록_status_필터_조회하면_해당_상태만_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "상품A", 100000);
    Product product2 = saveProduct(seller, "상품B", 200000);
    Order order1 = saveOrder(buyer, product1);
    saveOrder(buyer, product2);
    ReflectionTestUtils.setField(order1, "orderStatus", OrderStatus.CANCELED);
    orderRepository.saveAndFlush(order1);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/users/me/orders")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("status", "CANCELED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].status").value("CANCELED"));
  }

  @Test
  void 판매자가_내_판매_주문_목록_조회하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(get("/api/users/me/sales").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].productTitle").value("아이패드 미니 6세대"))
        .andExpect(jsonPath("$.data.content[0].price").value(430000))
        .andExpect(jsonPath("$.data.content[0].buyerNickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 판매_주문_목록은_다른_판매자의_주문을_포함하지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User otherSeller = saveUser("other@example.com", "타인판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "내상품", 100000);
    Product product2 = saveProduct(otherSeller, "타인상품", 200000);
    saveOrder(buyer, product1);
    saveOrder(buyer, product2);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(get("/api/users/me/sales").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].productTitle").value("내상품"));
  }

  @Test
  void 판매_주문_목록_status_필터_조회하면_해당_상태만_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "상품A", 100000);
    Product product2 = saveProduct(seller, "상품B", 200000);
    Order order1 = saveOrder(buyer, product1);
    saveOrder(buyer, product2);
    ReflectionTestUtils.setField(order1, "orderStatus", OrderStatus.CANCELED);
    orderRepository.saveAndFlush(order1);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            get("/api/users/me/sales")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("status", "CANCELED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].status").value("CANCELED"));
  }

  @Test
  void 주문이_없으면_빈_페이지를_반환한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(get("/api/users/me/orders").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(0)))
        .andExpect(jsonPath("$.data.totalElements").value(0));

    mockMvc
        .perform(get("/api/users/me/sales").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(0)))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void 목록_응답의_가격이_orderPrice_스냅샷을_사용한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    saveOrder(buyer, product);
    product.updateInfo(null, null, 500000);
    productRepository.saveAndFlush(product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(get("/api/users/me/orders").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].price").value(430000));
  }

  @Test
  void 인증되지_않은_사용자가_구매_주문_목록_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/me/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 인증되지_않은_사용자가_판매_주문_목록_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/me/sales"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 잘못된_status_값으로_구매_주문_목록_조회하면_400_INVALID_ENUM_VALUE로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/users/me/orders")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("status", "INVALID_STATUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ENUM_VALUE"));
  }

  @Test
  void 잘못된_status_값으로_판매_주문_목록_조회하면_400_INVALID_ENUM_VALUE로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            get("/api/users/me/sales")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("status", "INVALID_STATUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ENUM_VALUE"));
  }

  @Test
  void 잘못된_페이지_요청으로_구매_주문_목록_조회하면_400_INVALID_PAGINATION으로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/users/me/orders")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void 잘못된_페이지_요청으로_판매_주문_목록_조회하면_400_INVALID_PAGINATION으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            get("/api/users/me/sales")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    return productRepository.saveAndFlush(Product.create(seller, title, "생활기스 조금 있습니다.", price));
  }

  private Order saveOrder(User buyer, Product product) {
    product.reserve();
    productRepository.save(product);
    return orderRepository.save(Order.create(buyer, product));
  }
}
