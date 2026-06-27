package com.guingujig.yeolmumarket.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.MockPaymentResult;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class PaymentServiceTest {

  private final PaymentService paymentService;
  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  PaymentServiceTest(
      PaymentService paymentService,
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.paymentService = paymentService;
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
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
  void 구매자가_CREATED_주문을_PAID_결과로_결제하면_결제_생성에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    PaymentService.ProcessPaymentResult result =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    assertThat(result.created()).isTrue();
    PaymentResponse response = result.response();
    assertThat(response.paymentId()).isNotNull();
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.amount()).isEqualTo(430000);
    assertThat(response.method()).isEqualTo(PaymentMethod.MOCK_CARD);
    assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
    assertThat(response.paidAt()).isNotNull();
    assertThat(response.failedAt()).isNull();
  }

  @Test
  void 결제_성공_시_주문_상태가_PAID_상품_상태가_RESERVED로_유지된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    paymentService.processPayment(
        buyer.getId(),
        order.getId(),
        "idem-key-001",
        new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    Order paidOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reservedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(paidOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(reservedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 결제_실패_시_결제_상태가_FAILED_주문_상태가_CANCELED_상품_상태가_ON_SALE로_변경된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    PaymentService.ProcessPaymentResult result =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.FAILED));

    PaymentResponse response = result.response();
    assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.failedAt()).isNotNull();
    assertThat(response.paidAt()).isNull();

    Order canceledOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product onSaleProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(canceledOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(onSaleProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void result가_없으면_PAID로_처리한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    PaymentService.ProcessPaymentResult result =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, null));

    assertThat(result.response().status()).isEqualTo(PaymentStatus.PAID);
    assertThat(result.response().orderStatus()).isEqualTo(OrderStatus.PAID);
  }

  @Test
  void 결제_금액은_주문의_orderPrice를_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    Product freshProduct = productRepository.findById(product.getId()).orElseThrow();
    freshProduct.updateInfo(null, null, 999999);
    productRepository.saveAndFlush(freshProduct);

    PaymentService.ProcessPaymentResult result =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    assertThat(result.response().amount()).isEqualTo(430000);
    Payment savedPayment =
        paymentRepository.findByOrder_Id(order.getId()).orElseThrow();
    assertThat(savedPayment.getAmount()).isEqualTo(430000);
  }

  @Test
  void 같은_주문에_같은_멱등키로_재요청하면_기존_결제를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    PaymentService.ProcessPaymentResult first =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    PaymentService.ProcessPaymentResult replay =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    assertThat(replay.created()).isFalse();
    assertThat(replay.response().paymentId()).isEqualTo(first.response().paymentId());
    assertThat(paymentRepository.count()).isEqualTo(1);
  }

  @Test
  void 같은_주문에_다른_멱등키로_재요청하면_PAYMENT_ALREADY_EXISTS가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    paymentService.processPayment(
        buyer.getId(),
        order.getId(),
        "idem-key-001",
        new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    buyer.getId(),
                    order.getId(),
                    "idem-key-002",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS));
  }

  @Test
  void 다른_주문에서_사용된_멱등키로_결제하면_PAYMENT_ALREADY_EXISTS가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Product product2 = saveProduct(seller, "맥북 프로", 2000000);
    Order order1 = saveOrder(buyer, product1);
    Order order2 = saveOrder(buyer, product2);

    paymentService.processPayment(
        buyer.getId(),
        order1.getId(),
        "idem-key-001",
        new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    buyer.getId(),
                    order2.getId(),
                    "idem-key-001",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS));
  }

  @Test
  void 판매자가_결제를_요청하면_ORDER_ACCESS_DENIED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    seller.getId(),
                    order.getId(),
                    "idem-key-001",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 주문_참여자가_아닌_사용자가_결제를_요청하면_ORDER_ACCESS_DENIED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    other.getId(),
                    order.getId(),
                    "idem-key-001",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_주문_결제는_ORDER_NOT_FOUND가_발생한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    buyer.getId(),
                    Long.MAX_VALUE,
                    "idem-key-001",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  void CREATED가_아닌_주문_결제는_INVALID_ORDER_STATUS가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.PAID);
    orderRepository.saveAndFlush(order);

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    buyer.getId(),
                    order.getId(),
                    "idem-key-001",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
  }

  @Test
  void 멱등키가_null이면_VALIDATION_FAILED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    buyer.getId(),
                    order.getId(),
                    null,
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void 멱등키가_blank이면_VALIDATION_FAILED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    assertThatThrownBy(
            () ->
                paymentService.processPayment(
                    buyer.getId(),
                    order.getId(),
                    "   ",
                    new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  private void deleteAll() {
    paymentRepository.deleteAll();
    orderRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();
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
