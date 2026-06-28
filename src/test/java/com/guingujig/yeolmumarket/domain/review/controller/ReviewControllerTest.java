package com.guingujig.yeolmumarket.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.review.entity.Review;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
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
class ReviewControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final OrderRepository orderRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  ReviewControllerTest(
      MockMvc mockMvc,
      ReviewRepository reviewRepository,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      OrderRepository orderRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.reviewRepository = reviewRepository;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.orderRepository = orderRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 리뷰_작성에_성공하면_201로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5,\"content\":\" 시간 약속을 잘 지켜주셨어요. \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.reviewId").isNumber())
        .andExpect(jsonPath("$.data.orderId").value(order.getId()))
        .andExpect(jsonPath("$.data.reviewerId").value(buyer.getId()))
        .andExpect(jsonPath("$.data.revieweeId").value(seller.getId()))
        .andExpect(jsonPath("$.data.score").value(5))
        .andExpect(jsonPath("$.data.content").value("시간 약속을 잘 지켜주셨어요."))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 인증되지_않은_사용자가_리뷰를_작성하면_401로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5,\"content\":\"좋아요.\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 주문_참여자가_아닌_사용자가_리뷰를_작성하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5,\"content\":\"좋아요.\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void 거래_완료_전_리뷰_작성은_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.SHIPPING);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5,\"content\":\"좋아요.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
  }

  @Test
  void 중복_리뷰_작성은_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    reviewRepository.saveAndFlush(
        com.guingujig.yeolmumarket.domain.review.entity.Review.create(
            order, buyer, seller, 5, "좋아요."));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":4,\"content\":\"또 좋아요.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
  }

  @Test
  void 리뷰_요청값이_잘못되면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":0,\"content\":\"좋아요.\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5,\"content\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", order.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5,\"content\":\"" + "a".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 리뷰_수정에_성공하면_200으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/reviews/{reviewId}", order.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":4,\"content\":\" 응답은 조금 늦었습니다. \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.reviewId").value(review.getId()))
        .andExpect(jsonPath("$.data.score").value(4))
        .andExpect(jsonPath("$.data.content").value("응답은 조금 늦었습니다."))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 작성자가_아닌_사용자가_리뷰를_수정하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/reviews/{reviewId}", order.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":4}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));
  }

  @Test
  void 주문과_리뷰가_일치하지_않는_수정_요청은_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Product otherProduct = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Order otherOrder = saveOrderWithStatus(buyer, otherProduct, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/reviews/{reviewId}", otherOrder.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":4}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
  }

  @Test
  void 리뷰_수정값이_없으면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/reviews/{reviewId}", order.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 리뷰_삭제에_성공하면_200으로_응답하고_실제_삭제한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            delete("/api/orders/{orderId}/reviews/{reviewId}", order.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.deleted").value(true));

    assertThat(reviewRepository.findById(review.getId())).isEmpty();
  }

  @Test
  void 작성자가_아닌_사용자가_리뷰를_삭제하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    User other = saveUser("other@example.com", "타인");
    Product product = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            delete("/api/orders/{orderId}/reviews/{reviewId}", order.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));
  }

  @Test
  void 주문과_리뷰가_일치하지_않는_삭제_요청은_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller);
    Product otherProduct = saveProduct(seller);
    Order order = saveOrderWithStatus(buyer, product, OrderStatus.COMPLETED);
    Order otherOrder = saveOrderWithStatus(buyer, otherProduct, OrderStatus.COMPLETED);
    Review review = saveReview(order, buyer, seller, 5, "좋은 거래였습니다.");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(buyer);

    mockMvc
        .perform(
            delete("/api/orders/{orderId}/reviews/{reviewId}", otherOrder.getId(), review.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, "아이패드 미니 6세대", "생활기스", 430000);
  }

  private Order saveOrderWithStatus(User buyer, Product product, OrderStatus status) {
    product.reserve();
    productRepository.saveAndFlush(product);
    Order order = Order.create(buyer, product);
    ReflectionTestUtils.setField(order, "orderStatus", status);
    return orderRepository.saveAndFlush(order);
  }

  private Review saveReview(
      Order order, User reviewer, User reviewee, Integer score, String content) {
    return reviewRepository.saveAndFlush(Review.create(order, reviewer, reviewee, score, content));
  }
}
