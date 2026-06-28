package com.guingujig.yeolmumarket.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewResponse;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
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
class ReviewServiceTest {

  private final ReviewService reviewService;
  private final ReviewRepository reviewRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  ReviewServiceTest(
      ReviewService reviewService,
      ReviewRepository reviewRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.reviewService = reviewService;
    this.reviewRepository = reviewRepository;
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
  void 구매자가_COMPLETED_주문에_판매자_리뷰를_작성한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);

    ReviewResponse response =
        reviewService.createReview(buyer.getId(), order.getId(), 5, "  시간 약속을 잘 지켜주셨어요.  ");

    assertThat(response.reviewId()).isNotNull();
    assertThat(response.orderId()).isEqualTo(order.getId());
    assertThat(response.reviewerId()).isEqualTo(buyer.getId());
    assertThat(response.revieweeId()).isEqualTo(seller.getId());
    assertThat(response.score()).isEqualTo(5);
    assertThat(response.content()).isEqualTo("시간 약속을 잘 지켜주셨어요.");
    assertThat(response.createdAt().getOffset().getTotalSeconds()).isZero();
    assertThat(reviewRepository.existsByOrderIdAndReviewerId(order.getId(), buyer.getId()))
        .isTrue();
  }

  @Test
  void 판매자도_같은_주문에서_구매자에게_리뷰를_작성할_수_있다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);

    reviewService.createReview(buyer.getId(), order.getId(), 5, "좋은 거래였습니다.");
    ReviewResponse sellerReview =
        reviewService.createReview(seller.getId(), order.getId(), 4, "빠르게 확인해주셨습니다.");

    assertThat(sellerReview.reviewerId()).isEqualTo(seller.getId());
    assertThat(sellerReview.revieweeId()).isEqualTo(buyer.getId());
    assertThat(reviewRepository.count()).isEqualTo(2);
  }

  @Test
  void 주문_참여자가_아니면_리뷰를_작성할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);

    assertThatThrownBy(() -> reviewService.createReview(other.getId(), order.getId(), 5, "좋아요."))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
  }

  @Test
  void 존재하지_않는_주문에는_리뷰를_작성할_수_없다() {
    User buyer = saveUser("buyer@example.com", "열무구매자");

    assertThatThrownBy(() -> reviewService.createReview(buyer.getId(), Long.MAX_VALUE, 5, "좋아요."))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
  }

  @Test
  void COMPLETED가_아닌_주문에는_REVIEW_NOT_ALLOWED를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");

    for (OrderStatus status :
        new OrderStatus[] {
          OrderStatus.CREATED,
          OrderStatus.PAID,
          OrderStatus.SHIPPING,
          OrderStatus.CANCELED,
          OrderStatus.REFUND_REQUESTED,
          OrderStatus.REFUNDED,
          OrderStatus.DISPUTED
        }) {
      Product product = saveProduct(seller, "상품-" + status);
      Order order = saveOrderWithStatus(buyer, product, status);

      assertThatThrownBy(() -> reviewService.createReview(buyer.getId(), order.getId(), 5, "좋아요."))
          .isInstanceOfSatisfying(
              BusinessException.class,
              exception ->
                  assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED));
    }
  }

  @Test
  void 같은_주문에서_같은_작성자는_한_번만_리뷰를_작성할_수_있다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    reviewService.createReview(buyer.getId(), order.getId(), 5, "좋아요.");

    assertThatThrownBy(() -> reviewService.createReview(buyer.getId(), order.getId(), 4, "또 좋아요."))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
  }

  @Test
  void 동시에_같은_작성자가_리뷰를_작성하면_한_요청만_성공한다() throws InterruptedException {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    int threadCount = 2;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<ReviewResponse> successes = new CopyOnWriteArrayList<>();
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              successes.add(reviewService.createReview(buyer.getId(), order.getId(), 5, "좋아요."));
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

    assertThat(allDone).as("모든 리뷰 작성 스레드가 10초 내에 완료되어야 합니다").isTrue();
    assertThat(successes).hasSize(1);
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
    assertThat(reviewRepository.count()).isEqualTo(1);
  }

  @Test
  void 평점과_내용이_유효하지_않으면_VALIDATION_FAILED를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);

    assertThatThrownBy(() -> reviewService.createReview(buyer.getId(), order.getId(), 0, "좋아요."))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(() -> reviewService.createReview(buyer.getId(), order.getId(), 6, "좋아요."))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(() -> reviewService.createReview(buyer.getId(), order.getId(), 5, "   "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(
            () -> reviewService.createReview(buyer.getId(), order.getId(), 5, "a".repeat(256)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  private void deleteAll() {
    reviewRepository.deleteAll();
    orderRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller) {
    return saveProduct(seller, "아이패드 미니 6세대");
  }

  private Product saveProduct(User seller, String title) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, title, "생활기스 조금 있습니다.", 430000);
  }

  private Order saveOrderWithStatus(User buyer, Product product, OrderStatus status) {
    product.reserve();
    productRepository.saveAndFlush(product);
    Order order = Order.create(buyer, product);
    ReflectionTestUtils.setField(order, "orderStatus", status);
    return orderRepository.saveAndFlush(order);
  }
}
