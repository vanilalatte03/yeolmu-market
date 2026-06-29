package com.guingujig.yeolmumarket.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewRatingSummary;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ReviewRatingQueryServiceTest {

  private final ReviewRatingQueryService reviewRatingQueryService;
  private final ReviewService reviewService;
  private final ReviewRepository reviewRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  ReviewRatingQueryServiceTest(
      ReviewRatingQueryService reviewRatingQueryService,
      ReviewService reviewService,
      ReviewRepository reviewRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.reviewRatingQueryService = reviewRatingQueryService;
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
  void 받은_리뷰가_없으면_평점_0과_리뷰수_0을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");

    ReviewRatingSummary summary = reviewRatingQueryService.getSummary(seller.getId());

    assertThat(summary.averageRating()).isEqualTo(0.0);
    assertThat(summary.reviewCount()).isZero();
  }

  @Test
  void 리뷰_작성_수정_삭제_후_집계는_현재_저장_데이터를_반영한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User firstBuyer = saveUser("first-buyer@example.com", "첫구매자");
    User secondBuyer = saveUser("second-buyer@example.com", "둘째구매자");
    Order firstOrder =
        saveOrderWithStatus(firstBuyer, saveProduct(seller, "첫 상품"), OrderStatus.COMPLETED);
    Order secondOrder =
        saveOrderWithStatus(secondBuyer, saveProduct(seller, "둘째 상품"), OrderStatus.COMPLETED);

    var firstReview = reviewService.createReview(firstBuyer.getId(), firstOrder.getId(), 5, "좋아요.");
    var secondReview =
        reviewService.createReview(secondBuyer.getId(), secondOrder.getId(), 3, "괜찮아요.");

    assertSummary(seller.getId(), 4.0, 2);

    reviewService.updateReview(
        firstBuyer.getId(), firstOrder.getId(), firstReview.reviewId(), 4, null);

    assertSummary(seller.getId(), 3.5, 2);

    reviewService.deleteReview(secondBuyer.getId(), secondOrder.getId(), secondReview.reviewId());

    assertSummary(seller.getId(), 4.0, 1);

    reviewService.deleteReview(firstBuyer.getId(), firstOrder.getId(), firstReview.reviewId());

    assertSummary(seller.getId(), 0.0, 0);
  }

  private void assertSummary(Long revieweeId, double averageRating, long reviewCount) {
    ReviewRatingSummary summary = reviewRatingQueryService.getSummary(revieweeId);
    assertThat(summary.averageRating()).isEqualTo(averageRating);
    assertThat(summary.reviewCount()).isEqualTo(reviewCount);
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
