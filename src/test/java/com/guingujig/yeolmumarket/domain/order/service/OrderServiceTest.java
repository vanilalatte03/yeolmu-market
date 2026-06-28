package com.guingujig.yeolmumarket.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MyOrderListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MySaleListItemResponse;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class OrderServiceTest {

  private final OrderService orderService;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  OrderServiceTest(
      OrderService orderService,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.orderService = orderService;
    this.orderRepository = orderRepository;
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
  void 주문_생성에_성공하면_CREATED_상태의_주문을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    CreateOrderResponse response = orderService.createOrder(buyer.getId(), product.getId());

    assertThat(response.orderId()).isNotNull();
    assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
    assertThat(response.product().productId()).isEqualTo(product.getId());
    assertThat(response.buyer().userId()).isEqualTo(buyer.getId());
    assertThat(response.buyer().nickname()).isEqualTo("열무구매자");
    assertThat(response.seller().userId()).isEqualTo(seller.getId());
    assertThat(response.seller().nickname()).isEqualTo("열무판매자");
    assertThat(response.createdAt()).isNotNull();
  }

  @Test
  void 주문_생성_시_상품_상태가_RESERVED로_변경된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    orderService.createOrder(buyer.getId(), product.getId());

    Product reserved = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reserved.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 주문_생성_시_주문_가격이_상품_가격으로_스냅샷_저장된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    CreateOrderResponse response = orderService.createOrder(buyer.getId(), product.getId());

    assertThat(response.product().price()).isEqualTo(430000);
    assertThat(orderRepository.findById(response.orderId()).orElseThrow().getOrderPrice())
        .isEqualTo(430000);
  }

  @Test
  void 자신의_상품은_주문할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    assertThatThrownBy(() -> orderService.createOrder(seller.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANNOT_ORDER_OWN_PRODUCT));
  }

  @Test
  void 존재하지_않는_상품_주문은_실패한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 숨김_상품_주문은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveHiddenProduct(seller, "숨김 상품", 100000);

    assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 삭제_상품_주문은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProductWithStatus(seller, "삭제 상품", 100000, ProductStatus.DELETED);

    assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void RESERVED_상품_주문은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProductWithStatus(seller, "예약 상품", 100000, ProductStatus.RESERVED);

    assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE));
  }

  @Test
  void SOLD_OUT_상품_주문은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProductWithStatus(seller, "판매완료 상품", 100000, ProductStatus.SOLD_OUT);

    assertThatThrownBy(() -> orderService.createOrder(buyer.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE));
  }

  @Test
  void 동시에_같은_상품을_주문하면_성공_주문은_1건만_생성된다() throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    int threadCount = 5;
    List<User> buyers = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      buyers.add(saveUser("buyer" + i + "@example.com", "구매자" + i));
    }
    Long productId = product.getId();

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    for (User buyer : buyers) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              orderService.createOrder(buyer.getId(), productId);
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

    assertThat(allDone).as("모든 주문 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failures).hasSize(threadCount - 1);
    assertThat(failures)
        .allSatisfy(
            e -> {
              assertThat(e).isInstanceOf(BusinessException.class);
              ErrorCode code = ((BusinessException) e).getErrorCode();
              assertThat(code).isIn(ErrorCode.ORDER_ALREADY_EXISTS, ErrorCode.PRODUCT_NOT_ON_SALE);
            });
    assertThat(orderRepository.count()).isEqualTo(1);

    Product reserved = productRepository.findById(productId).orElseThrow();
    assertThat(reserved.getStatus()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 구매자가_주문_상세_조회에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    GetOrderResponse response = orderService.getOrder(buyer.getId(), created.orderId());

    assertThat(response.orderId()).isEqualTo(created.orderId());
    assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
    assertThat(response.product().productId()).isEqualTo(product.getId());
    assertThat(response.product().title()).isEqualTo("아이패드 미니 6세대");
    assertThat(response.product().price()).isEqualTo(430000);
    assertThat(response.product().status()).isEqualTo(ProductStatus.RESERVED);
    assertThat(response.buyer().userId()).isEqualTo(buyer.getId());
    assertThat(response.seller().userId()).isEqualTo(seller.getId());
    assertThat(response.createdAt()).isNotNull();
    assertThat(response.updatedAt()).isNotNull();
  }

  @Test
  void 판매자가_주문_상세_조회에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    GetOrderResponse response = orderService.getOrder(seller.getId(), created.orderId());

    assertThat(response.orderId()).isEqualTo(created.orderId());
    assertThat(response.buyer().userId()).isEqualTo(buyer.getId());
    assertThat(response.seller().userId()).isEqualTo(seller.getId());
  }

  @Test
  void 주문_참여자가_아닌_사용자는_주문_상세_조회에_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    assertThatThrownBy(() -> orderService.getOrder(other.getId(), created.orderId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_주문_상세_조회는_실패한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> orderService.getOrder(buyer.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  void 주문_상세_응답의_상품_가격이_orderPrice_스냅샷을_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    Product savedProduct = productRepository.findById(product.getId()).orElseThrow();
    savedProduct.updateInfo(null, null, 500000);
    productRepository.saveAndFlush(savedProduct);

    GetOrderResponse response = orderService.getOrder(buyer.getId(), created.orderId());

    assertThat(response.product().price()).isEqualTo(430000);
    assertThat(orderRepository.findById(created.orderId()).orElseThrow().getOrderPrice())
        .isEqualTo(430000);
  }

  @Test
  void 구매자가_CREATED_주문_취소에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    CancelOrderResponse response = orderService.cancelOrder(buyer.getId(), created.orderId());

    assertThat(response.orderId()).isEqualTo(created.orderId());
    assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(response.productStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.canceledAt()).isNotNull();
    assertThat(response.canceledAt().getOffset().getTotalSeconds()).isZero();
  }

  @Test
  void 주문_취소_시_주문_상태가_CANCELED로_변경된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    orderService.cancelOrder(buyer.getId(), created.orderId());

    Order canceled = orderRepository.findById(created.orderId()).orElseThrow();
    assertThat(canceled.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
  }

  @Test
  void 주문_취소_시_상품_상태가_ON_SALE로_변경된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    orderService.cancelOrder(buyer.getId(), created.orderId());

    Product restored = productRepository.findById(product.getId()).orElseThrow();
    assertThat(restored.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 주문_취소_후_같은_상품을_다시_주문할_수_있다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User buyer2 = saveUser("buyer2@example.com", "열무구매자2");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse first = orderService.createOrder(buyer.getId(), product.getId());
    orderService.cancelOrder(buyer.getId(), first.orderId());

    CreateOrderResponse second = orderService.createOrder(buyer2.getId(), product.getId());

    assertThat(second.orderId()).isNotNull();
    assertThat(second.status()).isEqualTo(OrderStatus.CREATED);
  }

  @Test
  void 주문_취소_응답의_canceledAt이_DB의_modifiedAt과_일치한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    CancelOrderResponse response = orderService.cancelOrder(buyer.getId(), created.orderId());

    Order canceledOrder = orderRepository.findById(created.orderId()).orElseThrow();
    assertThat(response.canceledAt())
        .isEqualTo(canceledOrder.getModifiedAt().atOffset(ZoneOffset.UTC));
  }

  @Test
  void 판매자는_주문을_취소할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    assertThatThrownBy(() -> orderService.cancelOrder(seller.getId(), created.orderId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 주문_참여자가_아닌_사용자는_주문을_취소할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());

    assertThatThrownBy(() -> orderService.cancelOrder(other.getId(), created.orderId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_주문_취소는_실패한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> orderService.cancelOrder(buyer.getId(), Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  void 이미_취소된_주문_취소는_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());
    orderService.cancelOrder(buyer.getId(), created.orderId());

    assertThatThrownBy(() -> orderService.cancelOrder(buyer.getId(), created.orderId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
  }

  @Test
  void CREATED가_아닌_상태의_주문_취소는_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);

    for (OrderStatus status :
        new OrderStatus[] {
          OrderStatus.PAID,
          OrderStatus.SHIPPING,
          OrderStatus.COMPLETED,
          OrderStatus.REFUND_REQUESTED,
          OrderStatus.REFUNDED,
          OrderStatus.DISPUTED
        }) {
      CreateOrderResponse created = orderService.createOrder(buyer.getId(), product.getId());
      Order order = orderRepository.findById(created.orderId()).orElseThrow();
      ReflectionTestUtils.setField(order, "orderStatus", status);
      orderRepository.saveAndFlush(order);
      Product p = productRepository.findById(product.getId()).orElseThrow();
      ReflectionTestUtils.setField(p, "status", ProductStatus.ON_SALE);
      productRepository.saveAndFlush(p);

      assertThatThrownBy(() -> orderService.cancelOrder(buyer.getId(), created.orderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
    }
  }

  @Test
  void 구매자가_내_구매_주문_목록_조회에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    saveOrder(buyer, product);

    PageResponse<MyOrderListItemResponse> response =
        orderService.getMyOrders(buyer.getId(), 0, 10, null);

    assertThat(response.content()).hasSize(1);
    MyOrderListItemResponse item = response.content().get(0);
    assertThat(item.productId()).isEqualTo(product.getId());
    assertThat(item.productTitle()).isEqualTo("아이패드 미니 6세대");
    assertThat(item.price()).isEqualTo(430000);
    assertThat(item.sellerNickname()).isEqualTo("열무판매자");
    assertThat(item.status()).isEqualTo(OrderStatus.CREATED);
    assertThat(item.createdAt()).isNotNull();
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 구매_주문_목록은_다른_사용자의_주문을_포함하지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product1 = saveProduct(seller, "상품A", 100000);
    Product product2 = saveProduct(seller, "상품B", 200000);
    saveOrder(buyer, product1);
    saveOrder(other, product2);

    PageResponse<MyOrderListItemResponse> response =
        orderService.getMyOrders(buyer.getId(), 0, 10, null);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).productTitle()).isEqualTo("상품A");
  }

  @Test
  void 구매_주문_목록_status_필터_조회에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "상품A", 100000);
    Product product2 = saveProduct(seller, "상품B", 200000);
    Order order1 = saveOrder(buyer, product1);
    saveOrder(buyer, product2);
    ReflectionTestUtils.setField(order1, "orderStatus", OrderStatus.CANCELED);
    orderRepository.saveAndFlush(order1);

    PageResponse<MyOrderListItemResponse> response =
        orderService.getMyOrders(buyer.getId(), 0, 10, OrderStatus.CANCELED);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).status()).isEqualTo(OrderStatus.CANCELED);
  }

  @Test
  void 판매자가_내_판매_주문_목록_조회에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    saveOrder(buyer, product);

    PageResponse<MySaleListItemResponse> response =
        orderService.getMySales(seller.getId(), 0, 10, null);

    assertThat(response.content()).hasSize(1);
    MySaleListItemResponse item = response.content().get(0);
    assertThat(item.productId()).isEqualTo(product.getId());
    assertThat(item.productTitle()).isEqualTo("아이패드 미니 6세대");
    assertThat(item.price()).isEqualTo(430000);
    assertThat(item.buyerNickname()).isEqualTo("열무구매자");
    assertThat(item.status()).isEqualTo(OrderStatus.CREATED);
    assertThat(item.createdAt()).isNotNull();
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 판매_주문_목록은_다른_판매자의_주문을_포함하지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User otherSeller = saveUser("other@example.com", "타인판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "내상품", 100000);
    Product product2 = saveProduct(otherSeller, "타인상품", 200000);
    saveOrder(buyer, product1);
    saveOrder(buyer, product2);

    PageResponse<MySaleListItemResponse> response =
        orderService.getMySales(seller.getId(), 0, 10, null);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).productTitle()).isEqualTo("내상품");
  }

  @Test
  void 판매_주문_목록_status_필터_조회에_성공한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "상품A", 100000);
    Product product2 = saveProduct(seller, "상품B", 200000);
    Order order1 = saveOrder(buyer, product1);
    saveOrder(buyer, product2);
    ReflectionTestUtils.setField(order1, "orderStatus", OrderStatus.CANCELED);
    orderRepository.saveAndFlush(order1);

    PageResponse<MySaleListItemResponse> response =
        orderService.getMySales(seller.getId(), 0, 10, OrderStatus.CANCELED);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).status()).isEqualTo(OrderStatus.CANCELED);
  }

  @Test
  void 주문이_없으면_빈_페이지를_반환한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    PageResponse<MyOrderListItemResponse> orders =
        orderService.getMyOrders(buyer.getId(), 0, 10, null);
    PageResponse<MySaleListItemResponse> sales =
        orderService.getMySales(buyer.getId(), 0, 10, null);

    assertThat(orders.content()).isEmpty();
    assertThat(orders.totalElements()).isZero();
    assertThat(sales.content()).isEmpty();
    assertThat(sales.totalElements()).isZero();
  }

  @Test
  void 구매_목록_응답의_가격이_orderPrice_스냅샷을_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", 430000);
    saveOrder(buyer, product);

    Product saved = productRepository.findById(product.getId()).orElseThrow();
    saved.updateInfo(null, null, 500000);
    productRepository.saveAndFlush(saved);

    PageResponse<MyOrderListItemResponse> response =
        orderService.getMyOrders(buyer.getId(), 0, 10, null);

    assertThat(response.content().get(0).price()).isEqualTo(430000);
  }

  @Test
  void 구매_주문_목록이_createdAt_DESC_id_DESC로_정렬된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product1 = saveProduct(seller, "상품A", 100000);
    Product product2 = saveProduct(seller, "상품B", 200000);
    Product product3 = saveProduct(seller, "상품C", 300000);
    Order order1 = saveOrder(buyer, product1);
    Order order2 = saveOrder(buyer, product2);
    Order order3 = saveOrder(buyer, product3);

    LocalDateTime base = LocalDateTime.of(2026, 6, 24, 10, 0);
    ReflectionTestUtils.setField(order1, "createdAt", base);
    ReflectionTestUtils.setField(order2, "createdAt", base.plusHours(1));
    ReflectionTestUtils.setField(order3, "createdAt", base.plusHours(2));
    orderRepository.saveAllAndFlush(List.of(order1, order2, order3));

    PageResponse<MyOrderListItemResponse> response =
        orderService.getMyOrders(buyer.getId(), 0, 10, null);

    List<Long> orderedIds =
        response.content().stream().map(MyOrderListItemResponse::orderId).toList();
    assertThat(orderedIds).containsExactly(order3.getId(), order2.getId(), order1.getId());
  }

  @Test
  void 구매_주문_목록_잘못된_페이지_요청은_INVALID_PAGINATION을_반환한다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> orderService.getMyOrders(buyer.getId(), -1, 10, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));

    assertThatThrownBy(() -> orderService.getMyOrders(buyer.getId(), 0, 0, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));

    assertThatThrownBy(() -> orderService.getMyOrders(buyer.getId(), 0, 101, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  private void deleteAll() {
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
}
