package com.guingujig.yeolmumarket.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
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
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  OrderServiceTest(
      OrderService orderService,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.orderService = orderService;
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

  private void deleteAll() {
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

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "hidden", true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }
}
