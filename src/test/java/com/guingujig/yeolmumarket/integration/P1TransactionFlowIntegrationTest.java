package com.guingujig.yeolmumarket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.guingujig.yeolmumarket.support.TestDataCleaner;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class P1TransactionFlowIntegrationTest {

  private static final AtomicLong TEST_SEQUENCE = new AtomicLong();
  private static final String PASSWORD = "Password123!";
  private static final String UTC_OFFSET_PATTERN = ".*(Z|\\+00:00)$";

  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final CategoryRepository categoryRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final PaymentRepository paymentRepository;
  private final TestDataCleaner testDataCleaner;

  @Autowired
  P1TransactionFlowIntegrationTest(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      CategoryRepository categoryRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      PaymentRepository paymentRepository,
      TestDataCleaner testDataCleaner) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.categoryRepository = categoryRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.paymentRepository = paymentRepository;
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
  void P1_핵심_거래_흐름은_구매확정까지_정상_완료된다() throws Exception {
    TestUser seller = signupAndLogin("seller", "열무판매자");
    TestUser buyer = signupAndLogin("buyer", "열무구매자");
    Category category = saveCategory();

    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categories", hasSize(1)))
        .andExpect(jsonPath("$.data.categories[0].categoryId").value(category.getId()))
        .andExpect(jsonPath("$.data.categories[0].name").value(category.getName()));

    Long productId = createProduct(seller, category.getId());

    createWish(buyer, productId, 1);
    assertWishListContains(buyer, productId);
    deleteWish(buyer, productId);
    assertWishListIsEmpty(buyer);

    Long orderId = createOrder(buyer, productId);
    assertOrderAndProductStatus(orderId, OrderStatus.CREATED, ProductStatus.RESERVED);

    Long paymentId = payOrder(buyer, orderId, uniqueIdempotencyKey(), status().isCreated());
    assertOrderAndProductStatus(orderId, OrderStatus.PAID, ProductStatus.RESERVED);
    assertPaymentStatus(paymentId, PaymentStatus.PAID);

    registerShipping(seller, orderId, "TRACK-P1-001");
    assertOrderAndProductStatus(orderId, OrderStatus.SHIPPING, ProductStatus.RESERVED);

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

    assertOrderAndProductStatus(orderId, OrderStatus.COMPLETED, ProductStatus.SOLD_OUT);
    assertPaymentStatus(paymentId, PaymentStatus.PAID);
  }

  @Test
  void 결제_전_CREATED_주문은_배송_증빙을_등록할_수_없다() throws Exception {
    TransactionFixture fixture = createCreatedOrderFixture();

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, fixture.seller().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("trackingNumber", "TRACK-BEFORE-PAY"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));

    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.CREATED, ProductStatus.RESERVED);
  }

  @Test
  void CREATED와_PAID_주문은_구매확정할_수_없다() throws Exception {
    TransactionFixture createdFixture = createCreatedOrderFixture();

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", createdFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, createdFixture.buyer().authorization()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));

    assertOrderAndProductStatus(
        createdFixture.orderId(), OrderStatus.CREATED, ProductStatus.RESERVED);

    TransactionFixture paidFixture = createCreatedOrderFixture();
    payOrder(
        paidFixture.buyer(), paidFixture.orderId(), uniqueIdempotencyKey(), status().isCreated());

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", paidFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, paidFixture.buyer().authorization()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));

    assertOrderAndProductStatus(paidFixture.orderId(), OrderStatus.PAID, ProductStatus.RESERVED);
  }

  @Test
  void 배송_증빙_등록_후에는_단순_주문_취소를_할_수_없다() throws Exception {
    TransactionFixture fixture = createCreatedOrderFixture();
    Long paymentId =
        payOrder(fixture.buyer(), fixture.orderId(), uniqueIdempotencyKey(), status().isCreated());
    registerShipping(fixture.seller(), fixture.orderId(), "TRACK-CANCEL-DENIED");

    mockMvc
        .perform(
            post("/api/orders/{orderId}/cancel", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, fixture.buyer().authorization()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));

    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.SHIPPING, ProductStatus.RESERVED);
    assertPaymentStatus(paymentId, PaymentStatus.PAID);
  }

  @Test
  void 주문_당사자가_아닌_사용자는_배송_증빙과_구매확정을_할_수_없다() throws Exception {
    TransactionFixture fixture = createCreatedOrderFixture();
    TestUser other = signupAndLogin("other", "타인");
    payOrder(fixture.buyer(), fixture.orderId(), uniqueIdempotencyKey(), status().isCreated());

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/shipping", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, other.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("trackingNumber", "TRACK-OTHER"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));

    registerShipping(fixture.seller(), fixture.orderId(), "TRACK-AUTH-001");

    mockMvc
        .perform(
            post("/api/orders/{orderId}/confirm", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, other.authorization()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));

    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.SHIPPING, ProductStatus.RESERVED);
  }

  @Test
  void 같은_주문의_결제_재요청은_멱등키에_따라_기존_결과를_반환하거나_거부한다() throws Exception {
    TransactionFixture fixture = createCreatedOrderFixture();
    String idempotencyKey = uniqueIdempotencyKey();

    Long paymentId =
        payOrder(fixture.buyer(), fixture.orderId(), idempotencyKey, status().isCreated());

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, fixture.buyer().authorization())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentRequestJson("PAID")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(paymentId))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
        .andExpect(jsonPath("$.data.productStatus").value("RESERVED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/payment", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, fixture.buyer().authorization())
                .header("Idempotency-Key", uniqueIdempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentRequestJson("PAID")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_EXISTS"));

    Payment payment = paymentRepository.findByOrder_Id(fixture.orderId()).orElseThrow();
    assertThat(payment.getId()).isEqualTo(paymentId);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
  }

  private TransactionFixture createCreatedOrderFixture() throws Exception {
    TestUser seller = signupAndLogin("seller", "열무판매자");
    TestUser buyer = signupAndLogin("buyer", "열무구매자");
    Long productId = createProduct(seller, saveCategory().getId());
    Long orderId = createOrder(buyer, productId);
    return new TransactionFixture(seller, buyer, productId, orderId);
  }

  private TestUser signupAndLogin(String emailPrefix, String nickname) throws Exception {
    String email = emailPrefix + "-" + TEST_SEQUENCE.incrementAndGet() + "@example.com";
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
    return new TestUser(userId, "Bearer " + accessToken);
  }

  private Category saveCategory() {
    return categoryRepository.saveAndFlush(
        Category.create("p1-cat-" + TEST_SEQUENCE.incrementAndGet()));
  }

  private Long createProduct(TestUser seller, Long categoryId) throws Exception {
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
                                "P1 거래 상품",
                                "description",
                                "P1 핵심 거래 흐름 검증 상품입니다.",
                                "price",
                                430000,
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

  private void createWish(TestUser buyer, Long productId, int expectedWishCount) throws Exception {
    mockMvc
        .perform(
            post("/api/products/{productId}/wishes", productId)
                .header(HttpHeaders.AUTHORIZATION, buyer.authorization()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.productId").value(productId))
        .andExpect(jsonPath("$.data.wished").value(true))
        .andExpect(jsonPath("$.data.wishCount").value(expectedWishCount));
  }

  private void deleteWish(TestUser buyer, Long productId) throws Exception {
    mockMvc
        .perform(
            delete("/api/products/{productId}/wishes", productId)
                .header(HttpHeaders.AUTHORIZATION, buyer.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.productId").value(productId))
        .andExpect(jsonPath("$.data.wished").value(false))
        .andExpect(jsonPath("$.data.wishCount").value(0));
  }

  private void assertWishListContains(TestUser buyer, Long productId) throws Exception {
    mockMvc
        .perform(
            get("/api/users/me/wishes").header(HttpHeaders.AUTHORIZATION, buyer.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].productId").value(productId))
        .andExpect(jsonPath("$.data.content[0].wishedAt", matchesPattern(UTC_OFFSET_PATTERN)))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  private void assertWishListIsEmpty(TestUser buyer) throws Exception {
    mockMvc
        .perform(
            get("/api/users/me/wishes").header(HttpHeaders.AUTHORIZATION, buyer.authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content", hasSize(0)))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  private Long createOrder(TestUser buyer, Long productId) throws Exception {
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

  private Long payOrder(
      TestUser buyer,
      Long orderId,
      String idempotencyKey,
      org.springframework.test.web.servlet.ResultMatcher expectedStatus)
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

  private void registerShipping(TestUser seller, Long orderId, String trackingNumber)
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

  private void assertOrderAndProductStatus(
      Long orderId, OrderStatus expectedOrderStatus, ProductStatus expectedProductStatus) {
    Order order = orderRepository.findWithDetailsById(orderId).orElseThrow();
    assertThat(order.getOrderStatus()).isEqualTo(expectedOrderStatus);
    assertThat(order.getProduct().getStatus()).isEqualTo(expectedProductStatus);
    assertThat(productRepository.findById(order.getProduct().getId()).orElseThrow().getStatus())
        .isEqualTo(expectedProductStatus);
  }

  private void assertPaymentStatus(Long paymentId, PaymentStatus expectedStatus) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(expectedStatus);
  }

  private String paymentRequestJson(String result) throws Exception {
    return json(Map.of("method", "MOCK_CARD", "result", result));
  }

  private String uniqueIdempotencyKey() {
    return "p1-idem-" + TEST_SEQUENCE.incrementAndGet();
  }

  private String json(Map<String, ?> payload) throws Exception {
    return objectMapper.writeValueAsString(payload);
  }

  private JsonNode readBody(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private record TestUser(Long userId, String authorization) {}

  private record TransactionFixture(
      TestUser seller, TestUser buyer, Long productId, Long orderId) {}
}
