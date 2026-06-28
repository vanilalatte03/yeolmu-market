package com.guingujig.yeolmumarket.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
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
class AdminProductControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 6, 24, 10, 0);

  @Autowired
  AdminProductControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 관리자는_숨김_상품_목록을_조회할_수_있다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Product hiddenProduct = saveHiddenProduct(seller, "아이패드 미니 6", 450000);
    saveProduct(seller, "공개 상품", 20000);
    saveDeletedHiddenProduct(seller, "삭제 숨김 상품", 30000);

    mockMvc
        .perform(
            get("/api/admin/products/hidden")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].productId").value(hiddenProduct.getId()))
        .andExpect(jsonPath("$.data.content[0].title").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.content[0].hidden").value(true))
        .andExpect(jsonPath("$.data.content[0].sellerNickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.content[0].updatedAt").exists())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 일반_사용자는_숨김_상품_목록을_조회할_수_없다() throws Exception {
    User user = saveUser("user@example.com", "열무사용자");

    mockMvc
        .perform(
            get("/api/admin/products/hidden").header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void 인증_없이_숨김_상품_목록을_조회하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/admin/products/hidden"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 숨김_상품_목록의_페이지_조건이_잘못되면_400으로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");

    mockMvc
        .perform(
            get("/api/admin/products/hidden")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .param("page", "-1")
                .param("size", "10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void 관리자는_상품을_숨김_처리할_수_있고_공개_조회에서_제외된다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hidden": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.hidden").value(true));

    Product hiddenProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(hiddenProduct.isHidden()).isTrue();
    assertThat(hiddenProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(0)))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void 관리자는_숨김_상품을_숨김_해제할_수_있다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveHiddenProduct(seller, "아이패드 미니 6", 450000);

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hidden": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.hidden").value(false));

    Product visibleProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(visibleProduct.isHidden()).isFalse();
    assertThat(visibleProduct.getStatus()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 일반_사용자는_상품_숨김_상태를_변경할_수_없다() throws Exception {
    User user = saveUser("user@example.com", "열무사용자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hidden": true
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void 인증_없이_상품_숨김_상태를_변경하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hidden": true
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void hidden_값이_없으면_400으로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 존재하지_않는_상품_숨김_상태_변경은_404로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hidden": true
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 삭제된_상품_숨김_상태_변경은_404로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithStatus(seller, "삭제 상품", 20000, ProductStatus.DELETED);

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/hidden", product.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hidden": true
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  private User saveAdmin(String email, String nickname) {
    User admin = saveUser(email, nickname);
    ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
    return userRepository.saveAndFlush(admin);
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
    product.changeHidden(true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveDeletedHiddenProduct(User seller, String title, Integer price) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    product.delete(DELETED_AT);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private String bearerToken(User user) {
    return "Bearer " + jwtTokenProvider.issueAccessToken(user);
  }
}
