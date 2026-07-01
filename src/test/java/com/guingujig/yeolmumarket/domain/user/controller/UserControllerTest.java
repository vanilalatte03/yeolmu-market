package com.guingujig.yeolmumarket.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.ActiveRefreshTokenRepository;
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
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
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
class UserControllerTest {

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final OrderRepository orderRepository;
  private final ReviewRepository reviewRepository;
  private final WishRepository wishRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  @MockitoBean private ActiveRefreshTokenRepository activeRefreshTokenRepository;
  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  @Autowired
  UserControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      OrderRepository orderRepository,
      ReviewRepository reviewRepository,
      WishRepository wishRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.orderRepository = orderRepository;
    this.reviewRepository = reviewRepository;
    this.wishRepository = wishRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 유저_공개_정보_조회에_성공하면_200으로_응답한다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(get("/api/users/{userId}", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.userId").value(user.getId()))
        .andExpect(jsonPath("$.data.nickname").value("열무구매자"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.averageRating").value(0.0))
        .andExpect(jsonPath("$.data.reviewCount").value(0))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 유저_공개_정보_조회는_받은_리뷰_평점과_리뷰_수를_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User firstBuyer = saveUser("first-buyer@example.com", "첫구매자");
    User secondBuyer = saveUser("second-buyer@example.com", "둘째구매자");
    Order firstOrder = saveOrderWithStatus(firstBuyer, saveProduct(seller), OrderStatus.COMPLETED);
    Order secondOrder =
        saveOrderWithStatus(secondBuyer, saveProduct(seller), OrderStatus.COMPLETED);
    saveReview(firstOrder, firstBuyer, seller, 5, "좋아요.");
    saveReview(secondOrder, secondBuyer, seller, 4, "괜찮아요.");

    mockMvc
        .perform(get("/api/users/{userId}", seller.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.averageRating").value(4.5))
        .andExpect(jsonPath("$.data.reviewCount").value(2));
  }

  @Test
  void 존재하지_않는_유저를_조회하면_404로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/{userId}", 999999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
  }

  @Test
  void Authorization_헤더_없이_유저_공개_정보를_조회할_수_있다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(get("/api/users/{userId}", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(user.getId()));
  }

  @Test
  void 응답에_이메일과_비밀번호가_포함되지_않는다() throws Exception {
    User user =
        userRepository.save(
            new User("customer@example.com", passwordEncoder.encode("Password123!"), "열무구매자"));

    mockMvc
        .perform(get("/api/users/{userId}", user.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").doesNotExist())
        .andExpect(jsonPath("$.data.password").doesNotExist());
  }

  @Test
  void 닉네임만_수정하면_200과_변경된_닉네임을_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.userId").value(user.getId()))
        .andExpect(jsonPath("$.data.email").value("customer@example.com"))
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")));
  }

  @Test
  void 비밀번호만_수정하면_200을_응답하고_새_비밀번호가_해시로_저장된다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"password": "NewPassword123!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("열무구매자"));

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("NewPassword123!", updated.getPassword())).isTrue();
    assertThat(updated.getPassword()).doesNotContain("NewPassword123!");
  }

  @Test
  void 닉네임과_비밀번호를_동시에_수정하면_200과_변경된_정보를_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임", "password": "NewPassword123!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"));

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updated.getNickname()).isEqualTo("새닉네임");
    assertThat(passwordEncoder.matches("NewPassword123!", updated.getPassword())).isTrue();
  }

  @Test
  void 수정할_값이_없는_요청은_400으로_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 공백_문자열_닉네임은_400으로_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "   "}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 공백_문자열_비밀번호는_400으로_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"password": "        "}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 미인증_요청은_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 인증된_사용자는_내_찜_목록을_조회할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    wishRepository.saveAndFlush(Wish.create(user, product));

    mockMvc
        .perform(
            get("/api/users/me/wishes")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].productId").value(product.getId()))
        .andExpect(jsonPath("$.data.content[0].title").value("아이패드 미니 6세대"))
        .andExpect(jsonPath("$.data.content[0].price").value(430000))
        .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.content[0].wishedAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 인증_없이_내_찜_목록을_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/me/wishes"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 잘못된_페이지_파라미터로_내_찜_목록을_조회하면_400으로_응답한다() throws Exception {
    User user = saveUser("user@example.com", "열무유저");

    mockMvc
        .perform(
            get("/api/users/me/wishes")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                .param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void 인증된_회원을_찾을_수_없으면_404로_응답한다() throws Exception {
    User user = saveUser("customer@example.com", "열무구매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(user);
    userRepository.delete(user);
    userRepository.flush();

    mockMvc
        .perform(
            put("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname": "새닉네임"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private String bearerToken(User user) {
    return "Bearer " + jwtTokenProvider.issueAccessToken(user);
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
