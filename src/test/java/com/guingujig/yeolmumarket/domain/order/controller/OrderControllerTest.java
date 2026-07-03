package com.guingujig.yeolmumarket.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import com.guingujig.yeolmumarket.support.TestDataCleaner;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final OrderRepository orderRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final TestDataCleaner testDataCleaner;

  @Autowired
  OrderControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      OrderRepository orderRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      TestDataCleaner testDataCleaner) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.orderRepository = orderRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.testDataCleaner = testDataCleaner;
  }

  @BeforeEach
  void setUp() {
    testDataCleaner.deleteAll();
  }

  @AfterEach
  void tearDown() {
    testDataCleaner.deleteAll();
  }

  @Test
  void 주문_생성에_성공하면_201로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/products/{productId}/orders", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").isNumber())
        .andExpect(jsonPath("$.data.product.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.product.title").value("아이패드 미니 6세대"))
        .andExpect(jsonPath("$.data.product.price").value(430000))
        .andExpect(jsonPath("$.data.buyer.userId").value(buyer.getId()))
        .andExpect(jsonPath("$.data.buyer.nickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.seller.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.seller.nickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.status").value("CREATED"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 인증되지_않은_사용자가_주문하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    mockMvc
        .perform(post("/api/products/{productId}/orders", product.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 자신의_상품을_주문하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/products/{productId}/orders", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CANNOT_ORDER_OWN_PRODUCT"));
  }

  @Test
  void 존재하지_않는_상품_주문은_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/products/{productId}/orders", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 숨김_상품_주문은_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveHiddenProduct(seller, "숨김 상품", 100000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/products/{productId}/orders", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 삭제_상품_주문은_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProductWithStatus(seller, "삭제 상품", 100000, ProductStatus.DELETED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/products/{productId}/orders", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void RESERVED_상품_주문은_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProductWithStatus(seller, "예약 상품", 100000, ProductStatus.RESERVED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/products/{productId}/orders", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ON_SALE"));
  }

  @Test
  void 구매자가_주문_상세_조회하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/orders/{orderId}", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.product.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.product.title").value("아이패드 미니 6세대"))
        .andExpect(jsonPath("$.data.product.price").value(430000))
        .andExpect(jsonPath("$.data.product.status").value("RESERVED"))
        .andExpect(jsonPath("$.data.buyer.userId").value(buyer.getId()))
        .andExpect(jsonPath("$.data.buyer.nickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.seller.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.seller.nickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.status").value("CREATED"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.payment").doesNotExist());
  }

  @Test
  void 판매자가_주문_상세_조회하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            get("/api/orders/{orderId}", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()));
  }

  @Test
  void 주문_참여자가_아닌_사용자가_조회하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            get("/api/orders/{orderId}", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_주문_상세_조회하면_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/orders/{orderId}", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void 인증되지_않은_사용자가_주문_상세_조회하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    mockMvc
        .perform(get("/api/orders/{orderId}", order.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 구매자가_CREATED_주문_취소하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/cancel", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("CANCELED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.canceledAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 판매자가_주문_취소하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/cancel", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 주문_참여자가_아닌_사용자가_주문_취소하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/cancel", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_주문_취소하면_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/cancel", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void 이미_취소된_주문_취소하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.CANCELED);
    orderRepository.saveAndFlush(order);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/cancel", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
  }

  @Test
  void 인증되지_않은_사용자가_주문_취소하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    mockMvc
        .perform(post("/api/orders/{orderId}/cancel", order.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 판매자가_PAID_주문_배송_증빙_등록하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = savePaidOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\" 1234-5678-9012 \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("SHIPPING"))
        .andExpect(jsonPath("$.data.trackingNumber").value("1234-5678-9012"))
        .andExpect(jsonPath("$.data.shippedAt", matchesPattern(".*(Z|\\+00:00)$")));

    Order shipped = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(shipped.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);
  }

  @Test
  void 인증되지_않은_사용자가_배송_증빙_등록하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = savePaidOrder(buyer, product);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"1234\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 구매자가_배송_증빙_등록하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = savePaidOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"1234\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_주문_배송_증빙_등록하면_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"1234\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void 잘못된_송장_번호로_배송_증빙_등록하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = savePaidOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"" + "1".repeat(101) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void PAID가_아닌_주문_배송_증빙_등록하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNumber\":\"1234\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
  }

  @Test
  void 구매자가_SHIPPING_주문_구매_확정하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data.confirmedAt", matchesPattern(".*(Z|\\+00:00)$")));

    Product soldOut = productRepository.findById(product.getId()).orElseThrow();
    assertThat(soldOut.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
  }

  @Test
  void 인증되지_않은_사용자가_구매_확정하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);

    mockMvc
        .perform(post("/api/orders/{orderId}/confirm", order.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 판매자가_구매_확정하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_주문_구매_확정하면_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void SHIPPING이_아닌_주문_구매_확정하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = savePaidOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
  }

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "hidden", true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private Order saveOrder(User buyer, Product product) {
    product.reserve();
    productRepository.save(product);
    return orderRepository.save(Order.create(buyer, product));
  }

  private Order savePaidOrder(User buyer, Product product) {
    Order order = saveOrder(buyer, product);
    order.markAsPaid();
    return orderRepository.saveAndFlush(order);
  }

  private Order saveShippingOrder(User buyer, Product product) {
    Order order = savePaidOrder(buyer, product);
    order.registerShipping("1234-5678-9012", LocalDateTime.of(2026, 6, 24, 10, 0));
    return orderRepository.saveAndFlush(order);
  }
}
