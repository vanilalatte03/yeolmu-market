package com.guingujig.yeolmumarket.domain.wish.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final WishRepository wishRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  WishControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      WishRepository wishRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.wishRepository = wishRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 인증된_사용자는_상품을_찜할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);

    mockMvc
        .perform(
            post("/api/products/{productId}/wishes", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.wished").value(true))
        .andExpect(jsonPath("$.data.wishCount").value(1));
  }

  @Test
  void 인증된_사용자는_상품_찜을_취소할_수_있다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    wishRepository.saveAndFlush(Wish.create(user, product));

    mockMvc
        .perform(
            delete("/api/products/{productId}/wishes", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.wished").value(false))
        .andExpect(jsonPath("$.data.wishCount").value(0));
  }

  @Test
  void 이미_찜한_상품을_다시_찜하면_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    wishRepository.saveAndFlush(Wish.create(user, product));

    mockMvc
        .perform(
            post("/api/products/{productId}/wishes", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("WISH_ALREADY_EXISTS"));
  }

  @Test
  void 없는_찜을_취소하면_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);

    mockMvc
        .perform(
            delete("/api/products/{productId}/wishes", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("WISH_NOT_FOUND"));
  }

  @Test
  void 인증되지_않은_사용자가_상품을_찜하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(post("/api/products/{productId}/wishes", 1L))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private String bearerToken(User user) {
    return "Bearer " + jwtTokenProvider.issueAccessToken(user);
  }

  private Product saveProduct(User seller) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
