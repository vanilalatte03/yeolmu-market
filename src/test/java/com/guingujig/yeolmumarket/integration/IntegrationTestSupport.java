package com.guingujig.yeolmumarket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.support.TestDataCleaner;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestSupport {

  protected static final String PASSWORD = "Password123!";
  protected static final String UTC_OFFSET_PATTERN = ".*(Z|\\+00:00)$";

  private static final AtomicLong TEST_SEQUENCE = new AtomicLong();

  @MockitoBean protected ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean protected RevokedAccessTokenRepository revokedAccessTokenRepository;

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected CategoryRepository categoryRepository;
  @Autowired protected OrderRepository orderRepository;
  @Autowired protected ProductRepository productRepository;
  @Autowired protected PaymentRepository paymentRepository;
  @Autowired protected RefundRequestRepository refundRequestRepository;
  @Autowired protected ReviewRepository reviewRepository;
  @Autowired protected TestDataCleaner testDataCleaner;

  @BeforeEach
  void setUp() {
    testDataCleaner.deleteAll();
  }

  @AfterEach
  void tearDown() {
    testDataCleaner.deleteAll();
  }

  protected TestUser signupAndLogin(String emailPrefix, String nickname) throws Exception {
    String email = emailPrefix + "-" + nextSequence() + "@example.com";
    String signupRequest = json(Map.of("email", email, "password", PASSWORD, "nickname", nickname));

    mockMvc
        .perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.nickname").value(nickname));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.user.email").value(email))
            .andReturn();

    JsonNode body = readBody(result);
    Long userId = body.requiredAt("/data/user/userId").longValue();
    String accessToken = body.requiredAt("/data/accessToken").asString();
    return new TestUser(userId, email, nickname, "Bearer " + accessToken);
  }

  protected Category saveCategory() {
    return categoryRepository.saveAndFlush(Category.create("integration-cat-" + nextSequence()));
  }

  protected Long createProduct(TestUser seller, Long categoryId) throws Exception {
    return createProduct(seller, categoryId, "통합 테스트 상품", "통합 테스트 흐름 검증 상품입니다.", 430000);
  }

  protected Long createProduct(
      TestUser seller, Long categoryId, String title, String description, Integer price)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/products")
                    .header(HttpHeaders.AUTHORIZATION, seller.authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "title",
                                title,
                                "description",
                                description,
                                "price",
                                price,
                                "categoryId",
                                categoryId))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.productId").isNumber())
            .andExpect(jsonPath("$.data.status").value("ON_SALE"))
            .andExpect(jsonPath("$.data.createdAt", matchesPattern(UTC_OFFSET_PATTERN)))
            .andReturn();

    return readBody(result).requiredAt("/data/productId").longValue();
  }

  protected Long createOrder(TestUser buyer, Long productId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/products/{productId}/orders", productId)
                    .header(HttpHeaders.AUTHORIZATION, buyer.authorization()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.orderId").isNumber())
            .andExpect(jsonPath("$.data.product.productId").value(productId))
            .andExpect(jsonPath("$.data.status").value("CREATED"))
            .andExpect(jsonPath("$.data.createdAt", matchesPattern(UTC_OFFSET_PATTERN)))
            .andReturn();

    return readBody(result).requiredAt("/data/orderId").longValue();
  }

  protected Long payOrder(
      TestUser buyer, Long orderId, String idempotencyKey, ResultMatcher expectedStatus)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/orders/{orderId}/payment", orderId)
                    .header(HttpHeaders.AUTHORIZATION, buyer.authorization())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(paymentRequestJson("PAID")))
            .andExpect(expectedStatus)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.paymentId").isNumber())
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
            .andExpect(jsonPath("$.data.productStatus").value("RESERVED"))
            .andExpect(jsonPath("$.data.paidAt", matchesPattern(UTC_OFFSET_PATTERN)))
            .andReturn();

    return readBody(result).requiredAt("/data/paymentId").longValue();
  }

  protected void registerShipping(TestUser seller, Long orderId, String trackingNumber)
      throws Exception {
    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", orderId)
                .header(HttpHeaders.AUTHORIZATION, seller.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("trackingNumber", " " + trackingNumber + " "))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").value(orderId))
        .andExpect(jsonPath("$.data.status").value("SHIPPING"))
        .andExpect(jsonPath("$.data.trackingNumber").value(trackingNumber))
        .andExpect(jsonPath("$.data.shippedAt", matchesPattern(UTC_OFFSET_PATTERN)));
  }

  protected void confirmOrder(TestUser buyer, Long orderId) throws Exception {
    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", orderId)
                .header(HttpHeaders.AUTHORIZATION, buyer.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.orderId").value(orderId))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data.confirmedAt", matchesPattern(UTC_OFFSET_PATTERN)));
  }

  protected TransactionFixture createCreatedOrderFixture() throws Exception {
    TestUser seller = signupAndLogin("seller", "열무판매자");
    TestUser buyer = signupAndLogin("buyer", "열무구매자");
    Long productId = createProduct(seller, saveCategory().getId());
    Long orderId = createOrder(buyer, productId);
    return new TransactionFixture(seller, buyer, productId, orderId);
  }

  protected PaidTransactionFixture createPaidOrderFixture() throws Exception {
    TransactionFixture fixture = createCreatedOrderFixture();
    Long paymentId =
        payOrder(fixture.buyer(), fixture.orderId(), uniqueIdempotencyKey(), status().isCreated());
    return new PaidTransactionFixture(
        fixture.seller(), fixture.buyer(), fixture.productId(), fixture.orderId(), paymentId);
  }

  protected PaidTransactionFixture createShippingOrderFixture() throws Exception {
    PaidTransactionFixture fixture = createPaidOrderFixture();
    registerShipping(fixture.seller(), fixture.orderId(), "TRACK-" + nextSequence());
    return fixture;
  }

  protected PaidTransactionFixture createCompletedOrderFixture() throws Exception {
    PaidTransactionFixture fixture = createShippingOrderFixture();
    confirmOrder(fixture.buyer(), fixture.orderId());
    return fixture;
  }

  protected void assertOrderAndProductStatus(
      Long orderId, OrderStatus expectedOrderStatus, ProductStatus expectedProductStatus) {
    Order order = orderRepository.findWithDetailsById(orderId).orElseThrow();
    assertThat(order.getOrderStatus()).isEqualTo(expectedOrderStatus);
    assertThat(order.getProduct().getStatus()).isEqualTo(expectedProductStatus);
    assertThat(productRepository.findById(order.getProduct().getId()).orElseThrow().getStatus())
        .isEqualTo(expectedProductStatus);
  }

  protected void assertPaymentStatus(Long paymentId, PaymentStatus expectedStatus) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(expectedStatus);
  }

  protected void assertRefundRequestStatus(
      Long refundRequestId, RefundRequestStatus expectedStatus) {
    RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId).orElseThrow();
    assertThat(refundRequest.getStatus()).isEqualTo(expectedStatus);
  }

  protected String paymentRequestJson(String result) throws Exception {
    return json(Map.of("method", "MOCK_CARD", "result", result));
  }

  protected String uniqueIdempotencyKey() {
    return "idem-" + nextSequence();
  }

  protected String json(Map<String, ?> payload) throws Exception {
    return objectMapper.writeValueAsString(payload);
  }

  protected JsonNode readBody(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  protected long nextSequence() {
    return TEST_SEQUENCE.incrementAndGet();
  }

  protected record TestUser(Long userId, String email, String nickname, String authorization) {}

  protected record TransactionFixture(
      TestUser seller, TestUser buyer, Long productId, Long orderId) {}

  protected record PaidTransactionFixture(
      TestUser seller, TestUser buyer, Long productId, Long orderId, Long paymentId) {}
}
