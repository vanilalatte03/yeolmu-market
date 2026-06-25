package com.guingujig.yeolmumarket.domain.product.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProductControllerTest {

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  UserProductControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 특정_유저_판매_상품_목록_조회에_성공하면_공개_상품을_페이지로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);

    mockMvc
        .perform(
            get("/api/users/{userId}/products", seller.getId())
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].productId").value(secondProduct.getId()))
        .andExpect(jsonPath("$.data.content[0].title").value("맥북 에어"))
        .andExpect(jsonPath("$.data.content[0].price").value(900000))
        .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.content[0].thumbnailUrl").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].sellerNickname").doesNotExist())
        .andExpect(jsonPath("$.data.content[1].productId").value(firstProduct.getId()))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 특정_유저_판매_상품_목록은_다른_유저_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른판매자");
    saveProduct(seller, "판매자 상품", 10000);
    saveProduct(other, "다른 유저 상품", 20000);

    mockMvc
        .perform(get("/api/users/{userId}/products", seller.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("판매자 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 특정_유저_판매_상품_목록은_숨김_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveHiddenProduct(seller, "숨김 상품", 20000);

    mockMvc
        .perform(get("/api/users/{userId}/products", seller.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 특정_유저_판매_상품_목록은_삭제_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveProductWithStatus(seller, "삭제 상품", 20000, ProductStatus.DELETED);

    mockMvc
        .perform(get("/api/users/{userId}/products", seller.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 특정_유저_판매_상품_목록은_상태로_필터링한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "판매 중 상품", 10000);
    saveProductWithStatus(seller, "예약 상품", 20000, ProductStatus.RESERVED);

    mockMvc
        .perform(get("/api/users/{userId}/products", seller.getId()).param("status", "RESERVED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("예약 상품"))
        .andExpect(jsonPath("$.data.content[0].status").value("RESERVED"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 존재하지_않는_유저의_판매_상품_목록_조회는_404로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/{userId}/products", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  void 내_판매_상품_목록_조회에_성공하면_본인_상품을_페이지로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);

    mockMvc
        .perform(
            get("/api/users/me/products").header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].productId").value(secondProduct.getId()))
        .andExpect(jsonPath("$.data.content[0].title").value("맥북 에어"))
        .andExpect(jsonPath("$.data.content[1].productId").value(firstProduct.getId()))
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void 내_판매_상품_목록은_다른_유저_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른판매자");
    saveProduct(seller, "내 상품", 10000);
    saveProduct(other, "다른 유저 상품", 20000);

    mockMvc
        .perform(
            get("/api/users/me/products").header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("내 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 내_판매_상품_목록은_상태로_필터링한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "판매 중 상품", 10000);
    saveProductWithStatus(seller, "판매 완료 상품", 20000, ProductStatus.SOLD_OUT);

    mockMvc
        .perform(
            get("/api/users/me/products")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                .param("status", "SOLD_OUT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("판매 완료 상품"))
        .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 내_판매_상품_목록은_숨김_상품을_포함한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveHiddenProduct(seller, "숨김 상품", 20000);

    mockMvc
        .perform(
            get("/api/users/me/products").header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].title").value("숨김 상품"))
        .andExpect(jsonPath("$.data.content[1].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void 내_판매_상품_목록은_기본적으로_삭제_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveProductWithStatus(seller, "삭제 상품", 20000, ProductStatus.DELETED);

    mockMvc
        .perform(
            get("/api/users/me/products").header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 인증_없이_내_판매_상품_목록을_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/me/products"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 잘못된_페이지_파라미터로_판매_상품_목록을_조회하면_400으로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/users/{userId}/products", Long.MAX_VALUE).param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    return productRepository.saveAndFlush(product);
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
    if (status == ProductStatus.DELETED) {
      ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.of(2026, 6, 24, 0, 0));
    }
    return productRepository.saveAndFlush(product);
  }

  private String bearerToken(User user) {
    return "Bearer " + jwtTokenProvider.issueAccessToken(user);
  }
}
