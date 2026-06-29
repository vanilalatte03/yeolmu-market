package com.guingujig.yeolmumarket.domain.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class RefundServiceTest {

  private final RefundService refundService;
  private final RefundRequestRepository refundRequestRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  RefundServiceTest(
      RefundService refundService,
      RefundRequestRepository refundRequestRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.refundService = refundService;
    this.refundRequestRepository = refundRequestRepository;
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
  }

  @AfterEach
  void tearDown() {
    deleteAll();
  }

  @Test
  void 구매자가_SHIPPING_주문에_환불_요청_생성에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);

    CreateRefundRequestResponse response =
        refundService.createRefundRequest(buyer.getId(), order.getId(), "  상품에 설명과 다른 하자가 있습니다.  ");

    assertThat(response.refundRequestId()).isNotNull();
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(RefundRequestStatus.REQUESTED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    assertThat(response.requestedAt()).isNotNull();
    assertThat(response.requestedAt().getOffset().getTotalSeconds()).isZero();

    RefundRequest savedRefund = refundRequestRepository.findByOrder_Id(order.getId()).orElseThrow();
    assertThat(savedRefund.getId()).isEqualTo(response.refundRequestId());
    assertThat(savedRefund.getRequester().getId()).isEqualTo(buyer.getId());
    assertThat(savedRefund.getReason()).isEqualTo("상품에 설명과 다른 하자가 있습니다.");
    assertThat(savedRefund.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
    assertThat(response.requestedAt())
        .isEqualTo(savedRefund.getRequestedAt().atOffset(ZoneOffset.UTC));

    Order refundRequestedOrder = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(refundRequestedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.RESERVED);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
  }

  @Test
  void 판매자와_타사용자는_환불_요청을_생성할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);

    assertThatThrownBy(
            () -> refundService.createRefundRequest(seller.getId(), order.getId(), "환불 요청"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));

    assertThatThrownBy(
            () -> refundService.createRefundRequest(other.getId(), order.getId(), "환불 요청"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));

    assertUnchanged(order, product, payment, OrderStatus.SHIPPING, 0);
  }

  @Test
  void 존재하지_않는_주문_환불_요청은_실패한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(
            () -> refundService.createRefundRequest(buyer.getId(), Long.MAX_VALUE, "환불 요청"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  void SHIPPING이_아닌_주문_환불_요청은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");

    for (OrderStatus status :
        new OrderStatus[] {
          OrderStatus.CREATED,
          OrderStatus.PAID,
          OrderStatus.COMPLETED,
          OrderStatus.CANCELED,
          OrderStatus.REFUND_REQUESTED,
          OrderStatus.REFUNDED,
          OrderStatus.DISPUTED
        }) {
      Product product = saveProduct(seller, "상품-" + status, 100000);
      Order order = saveOrderWithStatus(buyer, product, status);
      Payment payment = savePaidPayment(order);

      assertThatThrownBy(
              () -> refundService.createRefundRequest(buyer.getId(), order.getId(), "환불 요청"))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));

      assertUnchanged(order, product, payment, status, 0);
    }
  }

  @Test
  void 같은_주문에_환불_요청이_이미_있으면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);

    refundService.createRefundRequest(buyer.getId(), order.getId(), "첫 요청");

    assertThatThrownBy(
            () -> refundService.createRefundRequest(buyer.getId(), order.getId(), "두 번째 요청"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS));

    assertUnchanged(order, product, payment, OrderStatus.REFUND_REQUESTED, 1);
  }

  @Test
  void 잘못된_환불_사유는_환불_요청에_실패한다() {
    String[] invalidReasons = {null, "", "   ", "a".repeat(256)};

    for (String reason : invalidReasons) {
      assertThatThrownBy(() -> refundService.createRefundRequest(1L, 1L, reason))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
  }

  @Test
  void trim_후_255자인_환불_사유는_허용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    savePaidPayment(order);
    String reason = " " + "a".repeat(255) + " ";

    CreateRefundRequestResponse response =
        refundService.createRefundRequest(buyer.getId(), order.getId(), reason);

    assertThat(response.status()).isEqualTo(RefundRequestStatus.REQUESTED);
    assertThat(refundRequestRepository.findByOrder_Id(order.getId()).orElseThrow().getReason())
        .hasSize(255);
  }

  @Test
  void 잘못된_환불_사유로_실패하면_데이터는_변경되지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);

    assertThatThrownBy(() -> refundService.createRefundRequest(buyer.getId(), order.getId(), " "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertUnchanged(order, product, payment, OrderStatus.SHIPPING, 0);
  }

  @Test
  void 동시에_같은_주문에_환불_요청하면_한_요청만_성공한다() throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    int threadCount = 2;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<CreateRefundRequestResponse> successes = new CopyOnWriteArrayList<>();
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      int sequence = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              successes.add(
                  refundService.createRefundRequest(
                      buyer.getId(), order.getId(), "환불 요청 " + sequence));
            } catch (Exception e) {
              failures.add(e);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean allDone = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(allDone).as("모든 환불 요청 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successes).hasSize(1);
    assertThat(successes.get(0).status()).isEqualTo(RefundRequestStatus.REQUESTED);
    assertThat(successes.get(0).orderStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_ALREADY_EXISTS));

    assertUnchanged(order, product, payment, OrderStatus.REFUND_REQUESTED, 1);
  }

  private void assertUnchanged(
      Order order,
      Product product,
      Payment payment,
      OrderStatus expectedOrderStatus,
      long expectedRefundRequestCount) {
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(expectedOrderStatus);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.RESERVED);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
    assertThat(refundRequestRepository.countByOrder_Id(order.getId()))
        .isEqualTo(expectedRefundRequestCount);
  }

  private void deleteAll() {
    refundRequestRepository.deleteAll();
    paymentRepository.deleteAll();
    orderRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
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
    productRepository.saveAndFlush(product);
    return orderRepository.saveAndFlush(Order.create(buyer, product));
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

  private Order saveOrderWithStatus(User buyer, Product product, OrderStatus status) {
    Order order = saveOrder(buyer, product);
    ReflectionTestUtils.setField(order, "orderStatus", status);
    return orderRepository.saveAndFlush(order);
  }

  private Payment savePaidPayment(Order order) {
    return paymentRepository.saveAndFlush(
        Payment.createPaid(
            order,
            PaymentMethod.MOCK_CARD,
            "refund-idempotency-key-" + order.getId(),
            LocalDateTime.of(2026, 6, 24, 10, 5)));
  }
}
