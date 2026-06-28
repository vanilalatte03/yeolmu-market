package com.guingujig.yeolmumarket.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.MockPaymentResult;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentDetailResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentStatusResponse;
import com.guingujig.yeolmumarket.domain.payment.entity.Payment;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.payment.repository.PaymentRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@RecordApplicationEvents
class PaymentServiceTest {

  @Autowired private ApplicationEvents applicationEvents;

  private final PaymentService paymentService;
  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  PaymentServiceTest(
      PaymentService paymentService,
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PlatformTransactionManager transactionManager) {
    this.paymentService = paymentService;
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
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
    Payment savedPayment = paymentRepository.findByOrder_Id(order.getId()).orElseThrow();
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
  void 결제_실패_시_검색_캐시_무효화_이벤트가_발행된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    paymentService.processPayment(
        buyer.getId(),
        order.getId(),
        "idem-key-001",
        new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.FAILED));

    assertThat(applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count())
        .isEqualTo(1);
  }

  @Test
  void 첫_요청_FAILED_후_동일_멱등키로_PAID_body_재요청해도_기존_FAILED_결제를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);

    PaymentService.ProcessPaymentResult first =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.FAILED));

    assertThat(first.created()).isTrue();
    assertThat(first.response().status()).isEqualTo(PaymentStatus.FAILED);

    PaymentService.ProcessPaymentResult replay =
        paymentService.processPayment(
            buyer.getId(),
            order.getId(),
            "idem-key-001",
            new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));

    assertThat(replay.created()).isFalse();
    assertThat(replay.response().paymentId()).isEqualTo(first.response().paymentId());
    assertThat(replay.response().status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(paymentRepository.count()).isEqualTo(1);
  }

  @Test
  void 서로_다른_주문에_같은_멱등키로_동시_결제하면_하나만_성공하고_나머지는_PAYMENT_ALREADY_EXISTS가_발생한다()
      throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer1 = saveUser("buyer1@example.com", "구매자1");
    User buyer2 = saveUser("buyer2@example.com", "구매자2");
    Product product1 = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Product product2 = saveProduct(seller, "맥북 프로", 2000000);
    Order order1 = saveOrder(buyer1, product1);
    Order order2 = saveOrder(buyer2, product2);
    String sharedKey = "concurrent-idem-key";

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    AtomicInteger successCount = new AtomicInteger(0);
    CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

    executor.submit(
        () -> {
          try {
            startLatch.await();
            paymentService.processPayment(
                buyer1.getId(),
                order1.getId(),
                sharedKey,
                new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));
            successCount.incrementAndGet();
          } catch (Exception e) {
            failures.add(e);
          } finally {
            doneLatch.countDown();
          }
        });

    executor.submit(
        () -> {
          try {
            startLatch.await();
            paymentService.processPayment(
                buyer2.getId(),
                order2.getId(),
                sharedKey,
                new CreatePaymentRequest(PaymentMethod.MOCK_CARD, MockPaymentResult.PAID));
            successCount.incrementAndGet();
          } catch (Exception e) {
            failures.add(e);
          } finally {
            doneLatch.countDown();
          }
        });

    startLatch.countDown();
    boolean allDone = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(allDone).as("모든 결제 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) failures.get(0)).getErrorCode())
        .isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);
    assertThat(paymentRepository.count()).isEqualTo(1);
  }

  @Test
  void 구매자가_결제_상태를_조회하면_PaymentStatusResponse를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));

    PaymentStatusResponse response =
        paymentService.getPaymentStatus(buyer.getId(), payment.getId());

    assertThat(response.paymentId()).isEqualTo(payment.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
    assertThat(response.amount()).isEqualTo(430000);
    assertThat(response.paidAt()).isNotNull();
  }

  @Test
  void 판매자가_결제_상태를_조회하면_PaymentStatusResponse를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));

    PaymentStatusResponse response =
        paymentService.getPaymentStatus(seller.getId(), payment.getId());

    assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
  }

  @Test
  void 구매자가_결제_상세를_조회하면_PaymentDetailResponse를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));

    PaymentDetailResponse response =
        paymentService.getPaymentDetail(buyer.getId(), payment.getId());

    assertThat(response.paymentId()).isEqualTo(payment.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.amount()).isEqualTo(430000);
    assertThat(response.method()).isEqualTo(PaymentMethod.MOCK_CARD);
    assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
    assertThat(response.paidAt()).isNotNull();
    assertThat(response.canceledAt()).isNull();
  }

  @Test
  void FAILED_결제_상세_조회시_status가_FAILED이고_paidAt과_canceledAt이_null이다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createFailed(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));

    PaymentDetailResponse response =
        paymentService.getPaymentDetail(buyer.getId(), payment.getId());

    assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(response.paidAt()).isNull();
    assertThat(response.canceledAt()).isNull();
  }

  @Test
  void 존재하지_않는_결제_조회시_PAYMENT_NOT_FOUND가_발생한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> paymentService.getPaymentStatus(buyer.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
  }

  @Test
  void 주문_참여자가_아닌_사용자_결제_조회시_PAYMENT_ACCESS_DENIED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));

    assertThatThrownBy(() -> paymentService.getPaymentStatus(other.getId(), payment.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
  }

  @Test
  void 결제_상태_조회_후_결제_주문_상품_상태가_변경되지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment =
        paymentRepository.saveAndFlush(
            Payment.createPaid(
                order, PaymentMethod.MOCK_CARD, "idem-key-001", LocalDateTime.now(ZoneOffset.UTC)));

    paymentService.getPaymentStatus(buyer.getId(), payment.getId());

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
    assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 구매자가_PENDING_결제를_취소하면_결제_주문은_CANCELED_상품은_ON_SALE이_된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePendingPayment(order, "idem-key-001");

    CancelPaymentResponse response =
        paymentService.cancelPayment(buyer.getId(), payment.getId(), null);

    assertThat(response.paymentId()).isEqualTo(payment.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.canceledAt()).isNotNull();
    assertThat(response.canceledAt().getOffset()).isEqualTo(ZoneOffset.UTC);

    Payment canceledPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order canceledOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product onSaleProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(canceledPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(canceledPayment.getCanceledAt()).isNotNull();
    assertThat(canceledOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(onSaleProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 구매자가_PAID_결제를_취소하면_결제_주문은_REFUNDED_상품은_ON_SALE이_된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    CancelPaymentResponse response =
        paymentService.cancelPayment(buyer.getId(), payment.getId(), "배송 전 취소");

    assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.canceledAt()).isNotNull();

    Payment refundedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order refundedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product onSaleProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(refundedPayment.getCancelReason()).isEqualTo("배송 전 취소");
    assertThat(refundedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(onSaleProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 같은_결제를_동시_취소하면_하나만_성공하고_나머지는_INVALID_PAYMENT_STATUS가_발생한다() throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    AtomicInteger successCount = new AtomicInteger(0);
    CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

    for (int i = 0; i < 2; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              paymentService.cancelPayment(buyer.getId(), payment.getId(), "동시 취소");
              successCount.incrementAndGet();
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

    assertThat(allDone).as("모든 취소 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) failures.get(0)).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);

    Payment refundedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order refundedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product onSaleProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(refundedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(onSaleProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 배송_증빙_등록_트랜잭션이_먼저_주문을_잠그면_결제_취소는_커밋_후_INVALID_PAYMENT_STATUS가_발생한다()
      throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch shippingUpdatedLatch = new CountDownLatch(1);
    CountDownLatch cancelStartedLatch = new CountDownLatch(1);
    CountDownLatch commitShippingLatch = new CountDownLatch(1);
    AtomicReference<Throwable> shippingFailure = new AtomicReference<>();
    AtomicReference<Throwable> cancelFailure = new AtomicReference<>();
    AtomicInteger cancelSuccessCount = new AtomicInteger(0);

    executor.submit(
        () -> {
          try {
            transactionTemplate.executeWithoutResult(
                status -> {
                  Order lockedOrder =
                      orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
                  lockedOrder.registerShipping("1234-5678-9012", LocalDateTime.now(ZoneOffset.UTC));
                  orderRepository.flush();

                  shippingUpdatedLatch.countDown();
                  awaitLatch(cancelStartedLatch);
                  awaitLatch(commitShippingLatch);
                });
          } catch (Throwable e) {
            shippingFailure.set(e);
          }
        });

    executor.submit(
        () -> {
          try {
            awaitLatch(shippingUpdatedLatch);
            cancelStartedLatch.countDown();

            paymentService.cancelPayment(buyer.getId(), payment.getId(), "배송 등록 중 취소");
            cancelSuccessCount.incrementAndGet();
          } catch (Throwable e) {
            cancelFailure.set(e);
          }
        });

    try {
      assertThat(shippingUpdatedLatch.await(5, TimeUnit.SECONDS))
          .as("배송 등록 트랜잭션이 주문 락을 잡아야 합니다")
          .isTrue();
      assertThat(cancelStartedLatch.await(5, TimeUnit.SECONDS))
          .as("결제 취소 스레드가 배송 등록 커밋 전에 시작되어야 합니다")
          .isTrue();
      TimeUnit.MILLISECONDS.sleep(300);
    } finally {
      commitShippingLatch.countDown();
      executor.shutdown();
    }
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
        .as("배송 등록과 결제 취소 스레드가 10초 내에 완료되어야 합니다")
        .isTrue();

    assertThat(shippingFailure.get()).isNull();
    assertThat(cancelSuccessCount.get()).isZero();
    assertThat(cancelFailure.get()).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) cancelFailure.get()).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);

    Payment paidPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order shippingOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reservedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(paidPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(paidPayment.getCanceledAt()).isNull();
    assertThat(shippingOrder.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);
    assertThat(shippingOrder.getTrackingNumber()).isEqualTo("1234-5678-9012");
    assertThat(reservedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 취소_사유는_trim해서_저장하고_취소_응답에는_노출하지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    CancelPaymentResponse response =
        paymentService.cancelPayment(buyer.getId(), payment.getId(), "  단순 변심  ");

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(reloadedPayment.getCancelReason()).isEqualTo("단순 변심");
    assertThat(response.getClass().getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly(
            "paymentId", "orderId", "status", "orderStatus", "productStatus", "canceledAt");
  }

  @Test
  void 취소_사유가_blank이면_저장하지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    paymentService.cancelPayment(buyer.getId(), payment.getId(), "   ");

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(reloadedPayment.getCancelReason()).isNull();
  }

  @Test
  void 취소_사유가_255자를_초과하면_VALIDATION_FAILED가_발생하고_상태는_변경되지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    assertThatThrownBy(
            () -> paymentService.cancelPayment(buyer.getId(), payment.getId(), "a".repeat(256)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(reloadedPayment.getCanceledAt()).isNull();
    assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 판매자가_결제_취소를_요청하면_PAYMENT_ACCESS_DENIED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    assertThatThrownBy(() -> paymentService.cancelPayment(seller.getId(), payment.getId(), null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
  }

  @Test
  void 주문_참여자가_아닌_사용자가_결제_취소를_요청하면_PAYMENT_ACCESS_DENIED가_발생한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    assertThatThrownBy(() -> paymentService.cancelPayment(other.getId(), payment.getId(), null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_결제_취소는_PAYMENT_NOT_FOUND가_발생한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> paymentService.cancelPayment(buyer.getId(), Long.MAX_VALUE, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
  }

  @Test
  void FAILED_CANCELED_REFUNDED_결제는_취소할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    PaymentStatus[] invalidStatuses = {
      PaymentStatus.FAILED, PaymentStatus.CANCELED, PaymentStatus.REFUNDED
    };

    for (PaymentStatus invalidStatus : invalidStatuses) {
      Product product = saveProduct(seller, "상품-" + invalidStatus, 430000);
      Order order = saveOrder(buyer, product);
      Payment payment = savePaidPayment(order, "idem-key-" + invalidStatus);
      ReflectionTestUtils.setField(payment, "status", invalidStatus);
      paymentRepository.saveAndFlush(payment);

      assertThatThrownBy(() -> paymentService.cancelPayment(buyer.getId(), payment.getId(), null))
          .as("%s 결제는 취소할 수 없어야 합니다", invalidStatus)
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS));

      Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
      Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
      Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
      assertThat(reloadedPayment.getStatus()).isEqualTo(invalidStatus);
      assertThat(reloadedOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
      assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }
  }

  @Test
  void PAID_결제라도_배송_이후_주문_상태이면_INVALID_PAYMENT_STATUS가_발생하고_상태는_변경되지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    OrderStatus[] invalidOrderStatuses = {
      OrderStatus.SHIPPING,
      OrderStatus.COMPLETED,
      OrderStatus.REFUND_REQUESTED,
      OrderStatus.DISPUTED
    };

    for (OrderStatus invalidOrderStatus : invalidOrderStatuses) {
      Product product = saveProduct(seller, "상품-" + invalidOrderStatus, 430000);
      Order order = saveOrder(buyer, product);
      Payment payment = savePaidPayment(order, "idem-key-" + invalidOrderStatus);
      ReflectionTestUtils.setField(order, "orderStatus", invalidOrderStatus);
      orderRepository.saveAndFlush(order);

      assertThatThrownBy(() -> paymentService.cancelPayment(buyer.getId(), payment.getId(), null))
          .as("%s 주문 연결 결제는 취소할 수 없어야 합니다", invalidOrderStatus)
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS));

      Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
      Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
      Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
      assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
      assertThat(reloadedPayment.getCanceledAt()).isNull();
      assertThat(reloadedOrder.getOrderStatus()).isEqualTo(invalidOrderStatus);
      assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }
  }

  @Test
  void 결제_취소_성공_시_검색_캐시_무효화_이벤트가_발행된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveOrder(buyer, product);
    Payment payment = savePaidPayment(order, "idem-key-001");

    paymentService.cancelPayment(buyer.getId(), payment.getId(), null);

    assertThat(applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count())
        .isEqualTo(1);
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

  private static void awaitLatch(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("latch wait timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private void deleteAll() {
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
    productRepository.save(product);
    return orderRepository.save(Order.create(buyer, product));
  }
}
