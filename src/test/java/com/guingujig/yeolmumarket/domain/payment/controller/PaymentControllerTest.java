package com.guingujig.yeolmumarket.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  PaymentControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 구매자가_결제_성공_요청하면_201로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.paymentId").isNumber())
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.amount").value(430000))
        .andExpect(jsonPath("$.data.method").value("MOCK_CARD"))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
        .andExpect(jsonPath("$.data.productStatus").value("RESERVED"))
        .andExpect(jsonPath("$.data.paidAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.failedAt").doesNotExist());
  }

  @Test
  void 구매자가_결제_실패_요청하면_201로_응답하고_failedAt이_포함된다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"FAILED\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("FAILED"))
        .andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.failedAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.paidAt").doesNotExist());
  }

  @Test
  void 인증되지_않은_사용자가_결제하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Order order = saveOrder(buyer, product);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 판매자가_결제_요청하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 주문_참여자가_아닌_사용자가_결제_요청하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_주문_결제하면_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void CREATED가_아닌_주문_결제하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.PAID);
    orderRepository.saveAndFlush(order);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
  }

  @Test
  void 같은_멱등키로_재요청하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    paymentRepository.saveAndFlush(
        Payment.createPaid(
            order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PAID"));
  }

  @Test
  void 다른_멱등키로_재요청하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    paymentRepository.saveAndFlush(
        Payment.createPaid(
            order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_EXISTS"));
  }

  @Test
  void Idempotency_Key_헤더가_없으면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void Idempotency_Key가_100자를_초과하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);
    String longKey = "a".repeat(101);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", longKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"PAID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 잘못된_method_값으로_결제하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"VISA\",\"result\":\"PAID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 잘못된_result_값으로_결제하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header("Idempotency-Key", "idem-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"MOCK_CARD\",\"result\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 구매자가_결제_상태_조회하면_200과_상태_필드만_반환된다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}/status", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.amount").value(430000))
        .andExpect(jsonPath("$.data.paidAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.method").doesNotExist())
        .andExpect(jsonPath("$.data.canceledAt").doesNotExist())
        .andExpect(jsonPath("$.data.failedAt").doesNotExist());
  }

  @Test
  void 판매자가_결제_상태_조회하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}/status", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"));
  }

  @Test
  void 판매자가_결제_상세_조회하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"));
  }

  @Test
  void PAID_결제_상세_조회시_paidAt이_존재하고_canceledAt이_null로_포함된다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.amount").value(430000))
        .andExpect(jsonPath("$.data.method").value("MOCK_CARD"))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.paidAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.canceledAt", nullValue()))
        .andExpect(jsonPath("$.data.failedAt").doesNotExist());
  }

  @Test
  void FAILED_결제_상세_조회시_status가_FAILED이고_paidAt과_canceledAt이_null이며_failedAt_키가_없다()
      throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createFailed(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FAILED"))
        .andExpect(jsonPath("$.data.paidAt", nullValue()))
        .andExpect(jsonPath("$.data.canceledAt", nullValue()))
        .andExpect(jsonPath("$.data.failedAt").doesNotExist());
  }

  @Test
  void 주문_참여자가_아닌_사용자가_결제_상태_조회하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}/status", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PAYMENT_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_결제_조회하면_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}/status", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
  }

  @Test
  void 인증되지_않은_사용자가_결제_상태_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/payments/{paymentId}/status", 1L))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 결제_상태_조회_후_결제_주문_상품_상태가_변경되지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));
    ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.PAID);
    orderRepository.saveAndFlush(order);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            get("/api/payments/{paymentId}/status", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk());

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 구매자가_body_없이_PAID_결제를_취소하면_200과_취소_결과를_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/payments/{paymentId}/cancel", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.*", hasSize(6)))
        .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("REFUNDED"))
        .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.canceledAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.reason").doesNotExist())
        .andExpect(jsonPath("$.data.cancelReason").doesNotExist())
        .andExpect(jsonPath("$.data.amount").doesNotExist())
        .andExpect(jsonPath("$.data.method").doesNotExist())
        .andExpect(jsonPath("$.data.paidAt").doesNotExist())
        .andExpect(jsonPath("$.data.failedAt").doesNotExist());

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 구매자가_빈_JSON_body로_PENDING_결제를_취소하면_200과_취소_결과를_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePendingPayment(order, "idem-key-001");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/payments/{paymentId}/cancel", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.*", hasSize(6)))
        .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("CANCELED"))
        .andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.canceledAt", matchesPattern(".*(Z|\\+00:00)$")));

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 구매자가_reason을_담아_결제를_취소해도_응답에는_사유가_노출되지_않는다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/payments/{paymentId}/cancel", payment.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  단순 변심  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.*", hasSize(6)))
        .andExpect(jsonPath("$.data.reason").doesNotExist())
        .andExpect(jsonPath("$.data.cancelReason").doesNotExist());

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(reloadedPayment.getCancelReason()).isEqualTo("단순 변심");
  }

  @Test
  void 인증되지_않은_사용자가_결제_취소하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(post("/api/payments/{paymentId}/cancel", 1L))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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

  private Payment savePendingPayment(Order order, String idempotencyKey) {
    Payment payment =
        Payment.createPaid(
            order, PaymentMethod.MOCK_CARD, idempotencyKey, LocalDateTime.now(ZoneOffset.UTC));
    ReflectionTestUtils.setField(payment, "status", PaymentStatus.PENDING);
    ReflectionTestUtils.setField(payment, "paidAt", null);
    return paymentRepository.saveAndFlush(payment);
  }

  private Payment savePaidPayment(Order order, String idempotencyKey) {
    order.markAsPaid();
    orderRepository.saveAndFlush(order);
    return paymentRepository.saveAndFlush(
        Payment.createPaid(
            order, PaymentMethod.MOCK_CARD, idempotencyKey, LocalDateTime.now(ZoneOffset.UTC)));
  }
}
