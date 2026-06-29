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
import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RefundResolution;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequest;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import com.guingujig.yeolmumarket.domain.refund.repository.RefundRequestRepository;
import com.guingujig.yeolmumarket.domain.search.service.ProductSearchCacheEvictionEvent;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@RecordApplicationEvents
class RefundServiceTest {

  @Autowired private ApplicationEvents applicationEvents;

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

  @Test
  void 판매자가_REQUESTED_환불_요청_승인에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    long cacheEvictionEventsBefore =
        applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count();

    ApproveRefundRequestResponse response =
        refundService.approveRefundRequest(seller.getId(), refundRequest.getId());

    assertThat(response.refundRequestId()).isEqualTo(refundRequest.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(RefundRequestStatus.APPROVED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.approvedAt()).isNotNull();
    assertThat(response.approvedAt().getOffset().getTotalSeconds()).isZero();

    RefundRequest approvedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    assertThat(approvedRefund.getStatus()).isEqualTo(RefundRequestStatus.APPROVED);
    assertThat(approvedRefund.getApprovedAt()).isNotNull();
    assertThat(response.approvedAt())
        .isEqualTo(approvedRefund.getApprovedAt().atOffset(ZoneOffset.UTC));
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.REFUNDED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.ON_SALE);
    Payment refundedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(refundedPayment.getCanceledAt()).isEqualTo(approvedRefund.getApprovedAt());
    assertThat(applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count())
        .isEqualTo(cacheEvictionEventsBefore + 1);
  }

  @Test
  void 판매자가_REQUESTED_환불_요청_거절에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    long cacheEvictionEventsBefore =
        applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count();

    RejectRefundRequestResponse response =
        refundService.rejectRefundRequest(seller.getId(), refundRequest.getId(), "  정상 상품입니다.  ");

    assertThat(response.refundRequestId()).isEqualTo(refundRequest.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(RefundRequestStatus.DISPUTED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.DISPUTED);
    assertThat(response.rejectedAt()).isNotNull();
    assertThat(response.rejectedAt().getOffset().getTotalSeconds()).isZero();

    RefundRequest rejectedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    assertThat(rejectedRefund.getStatus()).isEqualTo(RefundRequestStatus.DISPUTED);
    assertThat(rejectedRefund.getSellerResponse()).isEqualTo("정상 상품입니다.");
    assertThat(response.rejectedAt())
        .isEqualTo(rejectedRefund.getRejectedAt().atOffset(ZoneOffset.UTC));
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.DISPUTED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.RESERVED);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
    assertThat(applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count())
        .isEqualTo(cacheEvictionEventsBefore);
  }

  @Test
  void blank_거절_사유는_null로_저장한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");

    refundService.rejectRefundRequest(seller.getId(), refundRequest.getId(), "   ");

    assertThat(
            refundRequestRepository
                .findById(refundRequest.getId())
                .orElseThrow()
                .getSellerResponse())
        .isNull();
  }

  @Test
  void 판매자가_DISPUTED_환불_요청을_REFUND로_종료하면_환불로_처리된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    long cacheEvictionEventsBefore =
        applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count();

    ResolveRefundRequestResponse response =
        refundService.resolveRefundRequest(
            seller.getId(), refundRequest.getId(), RefundResolution.REFUND, "  구매자 환불로 종료  ");

    assertThat(response.refundRequestId()).isEqualTo(refundRequest.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(RefundRequestStatus.CLOSED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.resolvedAt()).isNotNull();
    assertThat(response.resolvedAt().getOffset().getTotalSeconds()).isZero();

    RefundRequest resolvedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    assertThat(resolvedRefund.getStatus()).isEqualTo(RefundRequestStatus.CLOSED);
    assertThat(resolvedRefund.getSellerResponse()).isEqualTo("구매자 환불로 종료");
    assertThat(response.resolvedAt())
        .isEqualTo(resolvedRefund.getResolvedAt().atOffset(ZoneOffset.UTC));
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.REFUNDED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.ON_SALE);
    Payment refundedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(refundedPayment.getCanceledAt()).isEqualTo(resolvedRefund.getResolvedAt());
    assertThat(refundedPayment.getCancelReason()).isEqualTo("분쟁 환불 종료");
    assertThat(applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count())
        .isEqualTo(cacheEvictionEventsBefore + 1);
  }

  @Test
  void 판매자가_DISPUTED_환불_요청을_COMPLETE로_종료하면_거래_완료로_처리된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    long cacheEvictionEventsBefore =
        applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count();

    ResolveRefundRequestResponse response =
        refundService.resolveRefundRequest(
            seller.getId(), refundRequest.getId(), RefundResolution.COMPLETE, null);

    assertThat(response.refundRequestId()).isEqualTo(refundRequest.getId());
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.status()).isEqualTo(RefundRequestStatus.CLOSED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    assertThat(response.resolvedAt()).isNotNull();
    assertThat(response.resolvedAt().getOffset().getTotalSeconds()).isZero();

    RefundRequest resolvedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    assertThat(resolvedRefund.getStatus()).isEqualTo(RefundRequestStatus.CLOSED);
    assertThat(resolvedRefund.getSellerResponse()).isEqualTo("정상 상품입니다.");
    assertThat(response.resolvedAt())
        .isEqualTo(resolvedRefund.getResolvedAt().atOffset(ZoneOffset.UTC));
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.COMPLETED);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.SOLD_OUT);
    Payment paidPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(paidPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(paidPayment.getCanceledAt()).isNull();
    assertThat(applicationEvents.stream(ProductSearchCacheEvictionEvent.class).count())
        .isEqualTo(cacheEvictionEventsBefore + 1);
  }

  @Test
  void 구매자와_타사용자는_분쟁을_종료할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");

    assertThatThrownBy(
            () ->
                refundService.resolveRefundRequest(
                    buyer.getId(), refundRequest.getId(), RefundResolution.REFUND, "환불"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_ACCESS_DENIED));

    assertThatThrownBy(
            () ->
                refundService.resolveRefundRequest(
                    other.getId(), refundRequest.getId(), RefundResolution.COMPLETE, "완료"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_ACCESS_DENIED));

    assertDisputeUnchanged(refundRequest, order, product, payment);
  }

  @Test
  void 존재하지_않는_환불_요청_분쟁_종료는_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");

    assertThatThrownBy(
            () ->
                refundService.resolveRefundRequest(
                    seller.getId(), Long.MAX_VALUE, RefundResolution.REFUND, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  @Test
  void DISPUTED가_아닌_환불_요청_분쟁_종료는_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");

    for (RefundRequestStatus status :
        new RefundRequestStatus[] {
          RefundRequestStatus.REQUESTED, RefundRequestStatus.APPROVED, RefundRequestStatus.CLOSED
        }) {
      Product product = saveProduct(seller, "상품-" + status, 100000);
      Order order = saveShippingOrder(buyer, product);
      Payment payment = savePaidPayment(order);
      RefundRequest refundRequest = saveRequestedRefund(order, "환불 요청");
      ReflectionTestUtils.setField(refundRequest, "status", status);
      refundRequestRepository.saveAndFlush(refundRequest);

      assertThatThrownBy(
              () ->
                  refundService.resolveRefundRequest(
                      seller.getId(), refundRequest.getId(), RefundResolution.REFUND, null))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));

      assertRefundProcessingUnchanged(
          refundRequest, order, product, payment, status, OrderStatus.REFUND_REQUESTED);
    }
  }

  @Test
  void 주문이_DISPUTED가_아닌_환불_요청_분쟁_종료는_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.REFUND_REQUESTED);
    orderRepository.saveAndFlush(order);

    assertThatThrownBy(
            () ->
                refundService.resolveRefundRequest(
                    seller.getId(), refundRequest.getId(), RefundResolution.REFUND, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));

    assertRefundProcessingUnchanged(
        refundRequest,
        order,
        product,
        payment,
        RefundRequestStatus.DISPUTED,
        OrderStatus.REFUND_REQUESTED);
  }

  @Test
  void 잘못된_분쟁_종료_요청으로_실패하면_데이터는_변경되지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");

    assertThatThrownBy(
            () ->
                refundService.resolveRefundRequest(
                    seller.getId(), refundRequest.getId(), null, "종료"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertThatThrownBy(
            () ->
                refundService.resolveRefundRequest(
                    seller.getId(),
                    refundRequest.getId(),
                    RefundResolution.REFUND,
                    "a".repeat(256)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertDisputeUnchanged(refundRequest, order, product, payment);
  }

  @Test
  void 동시에_같은_분쟁을_종료하면_한_요청만_성공한다() throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveDisputedRefund(order, "상품에 설명과 다른 하자가 있습니다.", "정상 상품입니다.");
    int threadCount = 2;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<ResolveRefundRequestResponse> successes = new CopyOnWriteArrayList<>();
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    executor.submit(
        () -> {
          try {
            startLatch.await();
            successes.add(
                refundService.resolveRefundRequest(
                    seller.getId(), refundRequest.getId(), RefundResolution.REFUND, "환불"));
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
            successes.add(
                refundService.resolveRefundRequest(
                    seller.getId(), refundRequest.getId(), RefundResolution.COMPLETE, "완료"));
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

    assertThat(allDone).as("모든 분쟁 종료 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successes).hasSize(1);
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));

    RefundRequest resolvedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    Order resolvedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product resolvedProduct = productRepository.findById(product.getId()).orElseThrow();
    Payment resolvedPayment = paymentRepository.findById(payment.getId()).orElseThrow();

    assertThat(resolvedRefund.getStatus()).isEqualTo(RefundRequestStatus.CLOSED);
    if (successes.get(0).orderStatus() == OrderStatus.REFUNDED) {
      assertThat(resolvedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
      assertThat(resolvedProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
      assertThat(resolvedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    } else {
      assertThat(successes.get(0).orderStatus()).isEqualTo(OrderStatus.COMPLETED);
      assertThat(resolvedOrder.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
      assertThat(resolvedProduct.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
      assertThat(resolvedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }
  }

  @Test
  void 구매자와_타사용자는_환불_요청을_승인하거나_거절할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");

    assertThatThrownBy(
            () -> refundService.approveRefundRequest(buyer.getId(), refundRequest.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_ACCESS_DENIED));

    assertThatThrownBy(
            () -> refundService.rejectRefundRequest(other.getId(), refundRequest.getId(), "거절"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_ACCESS_DENIED));

    assertRefundProcessingUnchanged(
        refundRequest,
        order,
        product,
        payment,
        RefundRequestStatus.REQUESTED,
        OrderStatus.REFUND_REQUESTED);
  }

  @Test
  void 존재하지_않는_환불_요청_승인과_거절은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");

    assertThatThrownBy(() -> refundService.approveRefundRequest(seller.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_NOT_FOUND));

    assertThatThrownBy(
            () -> refundService.rejectRefundRequest(seller.getId(), Long.MAX_VALUE, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFUND_REQUEST_NOT_FOUND));
  }

  @Test
  void REQUESTED가_아닌_환불_요청_승인과_거절은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");

    for (RefundRequestStatus status :
        new RefundRequestStatus[] {
          RefundRequestStatus.APPROVED, RefundRequestStatus.DISPUTED, RefundRequestStatus.CLOSED
        }) {
      Product product = saveProduct(seller, "상품-" + status, 100000);
      Order order = saveShippingOrder(buyer, product);
      Payment payment = savePaidPayment(order);
      RefundRequest refundRequest = saveRequestedRefund(order, "환불 요청");
      ReflectionTestUtils.setField(refundRequest, "status", status);
      refundRequestRepository.saveAndFlush(refundRequest);

      assertThatThrownBy(
              () -> refundService.approveRefundRequest(seller.getId(), refundRequest.getId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));

      assertThatThrownBy(
              () -> refundService.rejectRefundRequest(seller.getId(), refundRequest.getId(), "거절"))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));

      assertRefundProcessingUnchanged(
          refundRequest, order, product, payment, status, OrderStatus.REFUND_REQUESTED);
    }
  }

  @Test
  void 잘못된_거절_사유로_실패하면_데이터는_변경되지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");

    assertThatThrownBy(
            () ->
                refundService.rejectRefundRequest(
                    seller.getId(), refundRequest.getId(), "a".repeat(256)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertRefundProcessingUnchanged(
        refundRequest,
        order,
        product,
        payment,
        RefundRequestStatus.REQUESTED,
        OrderStatus.REFUND_REQUESTED);
  }

  @Test
  void 동시에_같은_환불_요청을_승인하고_거절하면_한_요청만_성공한다() throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    Order order = saveShippingOrder(buyer, product);
    Payment payment = savePaidPayment(order);
    RefundRequest refundRequest = saveRequestedRefund(order, "상품에 설명과 다른 하자가 있습니다.");
    int threadCount = 2;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<Object> successes = new CopyOnWriteArrayList<>();
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    executor.submit(
        () -> {
          try {
            startLatch.await();
            successes.add(
                refundService.approveRefundRequest(seller.getId(), refundRequest.getId()));
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
            successes.add(
                refundService.rejectRefundRequest(seller.getId(), refundRequest.getId(), "거절"));
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

    assertThat(allDone).as("모든 환불 처리 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successes).hasSize(1);
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFUND_REQUEST_STATUS));

    RefundRequest processedRefund =
        refundRequestRepository.findById(refundRequest.getId()).orElseThrow();
    Order processedOrder = orderRepository.findById(order.getId()).orElseThrow();
    Product processedProduct = productRepository.findById(product.getId()).orElseThrow();
    Payment processedPayment = paymentRepository.findById(payment.getId()).orElseThrow();

    if (processedRefund.getStatus() == RefundRequestStatus.APPROVED) {
      assertThat(processedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
      assertThat(processedProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
      assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    } else {
      assertThat(processedRefund.getStatus()).isEqualTo(RefundRequestStatus.DISPUTED);
      assertThat(processedOrder.getOrderStatus()).isEqualTo(OrderStatus.DISPUTED);
      assertThat(processedProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
      assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }
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

  private void assertRefundProcessingUnchanged(
      RefundRequest refundRequest,
      Order order,
      Product product,
      Payment payment,
      RefundRequestStatus expectedRefundStatus,
      OrderStatus expectedOrderStatus) {
    assertThat(refundRequestRepository.findById(refundRequest.getId()).orElseThrow().getStatus())
        .isEqualTo(expectedRefundStatus);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(expectedOrderStatus);
    assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.RESERVED);
    assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PAID);
  }

  private void assertDisputeUnchanged(
      RefundRequest refundRequest, Order order, Product product, Payment payment) {
    assertRefundProcessingUnchanged(
        refundRequest, order, product, payment, RefundRequestStatus.DISPUTED, OrderStatus.DISPUTED);
    assertThat(
            refundRequestRepository.findById(refundRequest.getId()).orElseThrow().getResolvedAt())
        .isNull();
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
