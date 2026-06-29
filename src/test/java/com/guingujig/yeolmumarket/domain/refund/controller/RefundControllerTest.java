package com.guingujig.yeolmumarket.domain.refund.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
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
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
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
class RefundControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final RefundRequestRepository refundRequestRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  RefundControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      RefundRequestRepository refundRequestRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.refundRequestRepository = refundRequestRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 구매자가_SHIPPING_주문에_환불_요청하면_201로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  상품에 설명과 다른 하자가 있습니다.  \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").isNumber())
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("REQUESTED"))
        .andExpect(jsonPath("$.data.orderStatus").value("REFUND_REQUESTED"))
        .andExpect(jsonPath("$.data.requestedAt", matchesPattern(".*(Z|\\+00:00)$")));

    RefundRequest savedRefund = refundRequestRepository.findByOrder_Id(order.getId()).orElseThrow();
    assertThat(savedRefund.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
    assertThat(savedRefund.getReason()).isEqualTo("상품에 설명과 다른 하자가 있습니다.");
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.REFUND_REQUESTED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.RESERVED);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
  }

  @Test
  void 인증되지_않은_사용자가_환불_요청하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"환불 요청\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 판매자가_환불_요청하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"환불 요청\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_주문_환불_요청하면_404로_응답한다() throws Exception {
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"환불 요청\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void SHIPPING이_아닌_주문_환불_요청하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = savePaidOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"환불 요청\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
  }

  @Test
  void 같은_주문에_이미_환불_요청이_있으면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"첫 요청\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"두 번째 요청\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ALREADY_EXISTS"));
  }

  @Test
  void 잘못된_환불_사유로_환불_요청하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "a".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 판매자가_환불_요청을_승인하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequest.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.approvedAt", matchesPattern(".*(Z|\\+00:00)$")));

    assertThat(refundRequestRepository.findById(refundRequest.getId()).orElseThrow().getStatus())
        .isEqualTo(RefundRequestStatus.APPROVED);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.REFUNDED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.ON_SALE);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void 판매자가_환불_요청을_거절하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  정상 상품입니다.  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequest.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("DISPUTED"))
        .andExpect(jsonPath("$.data.orderStatus").value("DISPUTED"))
        .andExpect(jsonPath("$.data.rejectedAt", matchesPattern(".*(Z|\\+00:00)$")));

    RefundRequest rejectedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    assertThat(rejectedRefund.getStatus()).isEqualTo(RefundRequestStatus.DISPUTED);
    assertThat(rejectedRefund.getSellerResponse()).isEqualTo("정상 상품입니다.");
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.DISPUTED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.RESERVED);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
  }

  @Test
  void 판매자가_body_없이_환불_요청을_거절하면_거절_사유는_null로_저장된다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISPUTED"));

    assertThat(
            refundRequestRepository
                .findById(refundRequest.getId())
                .orElseThrow()
                .getSellerResponse())
        .isNull();
  }

  @Test
  void 판매자가_분쟁을_REFUND로_종료하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"REFUND\",\"reason\":\"  구매자 환불로 종료  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequest.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("CLOSED"))
        .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.resolvedAt", matchesPattern(".*(Z|\\+00:00)$")));

    RefundRequest resolvedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    assertThat(resolvedRefund.getStatus()).isEqualTo(RefundRequestStatus.CLOSED);
    assertThat(resolvedRefund.getSellerResponse()).isEqualTo("구매자 환불로 종료");
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.REFUNDED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.ON_SALE);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void 판매자가_분쟁을_COMPLETE로_종료하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"COMPLETE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequest.getId()))
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.status").value("CLOSED"))
        .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data.resolvedAt", matchesPattern(".*(Z|\\+00:00)$")));

    assertThat(refundRequestRepository.findById(refundRequest.getId()).orElseThrow().getStatus())
        .isEqualTo(RefundRequestStatus.CLOSED);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.COMPLETED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.SOLD_OUT);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
  }

  @Test
  void 인증되지_않은_사용자가_분쟁을_종료하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"REFUND\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 구매자가_분쟁을_종료하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"REFUND\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_환불_요청을_분쟁_종료하면_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"REFUND\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));
  }

  @Test
  void DISPUTED가_아닌_환불_요청을_분쟁_종료하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"REFUND\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REFUND_REQUEST_STATUS"));
  }

  @Test
  void 잘못된_분쟁_종료_요청이면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"INVALID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"REFUND\",\"reason\":\"" + "a".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 인증되지_않은_사용자가_환불_요청을_승인하거나_거절하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");

    mockMvc
        .perform(post("/api/refund/{refundId}/approve", refundRequest.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", refundRequest.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"거절\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 구매자가_환불_요청을_승인하거나_거절하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));

    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", refundRequest.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"거절\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
  }

  @Test
  void 존재하지_않는_환불_요청을_승인하거나_거절하면_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));

    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"거절\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));
  }

  @Test
  void REQUESTED가_아닌_환불_요청을_승인하거나_거절하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    Product approveProduct = saveProduct(seller, "승인 실패 상품", 100000);
    Order approveOrder = saveShippingOrder(buyer, approveProduct);
    savePaidPayment(approveOrder);
    RefundRequest approveRefund = saveRequestedRefund(approveOrder, "환불 요청");
    ReflectionTestUtils.setField(approveRefund, "status", RefundRequestStatus.APPROVED);
    refundRequestRepository.saveAndFlush(approveRefund);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", approveRefund.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REFUND_REQUEST_STATUS"));

    Product rejectProduct = saveProduct(seller, "거절 실패 상품", 100000);
    Order rejectOrder = saveShippingOrder(buyer, rejectProduct);
    savePaidPayment(rejectOrder);
    RefundRequest rejectRefund = saveRequestedRefund(rejectOrder, "환불 요청");
    ReflectionTestUtils.setField(rejectRefund, "status", RefundRequestStatus.DISPUTED);
    refundRequestRepository.saveAndFlush(rejectRefund);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", rejectRefund.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"거절\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REFUND_REQUEST_STATUS"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
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

  private RefundRequest saveRequestedRefund(Order order, String reason) {
    order.requestRefund();
    orderRepository.saveAndFlush(order);
    return refundRequestRepository.saveAndFlush(
        RefundRequest.create(
            order, order.getBuyer(), reason, LocalDateTime.of(2026, 6, 24, 10, 10)));
  }

  private RefundRequest saveDisputedRefund(Order order, String reason, String sellerResponse) {
    RefundRequest refundRequest = saveRequestedRefund(order, reason);
    refundRequest.rejectToDispute(sellerResponse, LocalDateTime.of(2026, 6, 24, 10, 20));
    order.rejectRefund();
    orderRepository.saveAndFlush(order);
    return refundRequestRepository.saveAndFlush(refundRequest);
  }

  private Payment savePaidPayment(Order order) {
    return paymentRepository.saveAndFlush(
        Payment.createPaid(
            order,
            PaymentMethod.MOCK_CARD,
            "refund-controller-idempotency-key-" + order.getId(),
            LocalDateTime.of(2026, 6, 24, 10, 5)));
  }
}
