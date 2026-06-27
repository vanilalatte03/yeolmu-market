package com.guingujig.yeolmumarket.domain.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.security.JwtTokenProvider;
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
class CategoryControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  CategoryControllerTest(
      MockMvc mockMvc,
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Test
  void 카테고리_목록_조회에_성공한다() throws Exception {
    Category firstCategory = saveCategory("디지털기기");
    Category secondCategory = saveCategory("가구");

    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.categories", hasSize(2)))
        .andExpect(jsonPath("$.data.categories[0].categoryId").value(firstCategory.getId()))
        .andExpect(jsonPath("$.data.categories[0].name").value("디지털기기"))
        .andExpect(jsonPath("$.data.categories[1].categoryId").value(secondCategory.getId()))
        .andExpect(jsonPath("$.data.categories[1].name").value("가구"));
  }

  @Test
  void 관리자는_카테고리를_생성할_수_있다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");

    mockMvc
        .perform(
            post("/api/admin/categories")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털기기"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.categoryId").isNumber())
        .andExpect(jsonPath("$.data.name").value("디지털기기"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));

    assertThat(categoryRepository.existsByName("디지털기기")).isTrue();
  }

  @Test
  void 관리자는_카테고리를_수정할_수_있다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    Category category = saveCategory("디지털기기");

    mockMvc
        .perform(
            put("/api/admin/categories/{categoryId}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털가전"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.categoryId").value(category.getId()))
        .andExpect(jsonPath("$.data.name").value("디지털가전"))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")));

    assertThat(categoryRepository.findById(category.getId()).orElseThrow().getName())
        .isEqualTo("디지털가전");
  }

  @Test
  void 관리자는_카테고리를_삭제할_수_있다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    Category category = saveCategory("디지털기기");

    mockMvc
        .perform(
            delete("/api/admin/categories/{categoryId}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.deleted").value(true));
  }

  @Test
  void 일반_사용자는_관리자_카테고리_API를_호출할_수_없다() throws Exception {
    User user = saveUser("user@example.com", "열무사용자");

    mockMvc
        .perform(
            post("/api/admin/categories")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털기기"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void 인증_없이_관리자_카테고리_API를_호출하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털기기"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 카테고리명이_비어_있으면_400으로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");

    mockMvc
        .perform(
            post("/api/admin/categories")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": " "
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 중복_카테고리명으로_생성하면_409로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    saveCategory("디지털기기");

    mockMvc
        .perform(
            post("/api/admin/categories")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털기기"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
  }

  @Test
  void 중복_카테고리명으로_수정하면_409로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    saveCategory("디지털기기");
    Category category = saveCategory("가구");

    mockMvc
        .perform(
            put("/api/admin/categories/{categoryId}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털기기"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
  }

  @Test
  void 존재하지_않는_카테고리_수정은_404로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");

    mockMvc
        .perform(
            put("/api/admin/categories/{categoryId}", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "디지털기기"
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  void 존재하지_않는_카테고리_삭제는_404로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");

    mockMvc
        .perform(
            delete("/api/admin/categories/{categoryId}", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  void 상품이_연결된_카테고리_삭제는_409로_응답한다() throws Exception {
    User admin = saveAdmin("admin@example.com", "열무관리자");
    User seller = saveUser("seller@example.com", "열무판매자");
    Category category = saveCategory("디지털기기");
    saveProduct(seller, category);

    mockMvc
        .perform(
            delete("/api/admin/categories/{categoryId}", category.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
  }

  @Test
  void 카테고리별_상품_조회_API는_구현하지_않는다() throws Exception {
    mockMvc
        .perform(get("/api/categories/{categoryId}/products", 1L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  private Category saveCategory(String name) {
    return categoryRepository.saveAndFlush(Category.create(name));
  }

  private User saveAdmin(String email, String nickname) {
    User admin = saveUser(email, nickname);
    ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
    return userRepository.saveAndFlush(admin);
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private void saveProduct(User seller, Category category) {
    Product product = Product.create(seller, "아이패드 미니 6", "생활기스 조금 있습니다.", 450000);
    ReflectionTestUtils.setField(product, "category", category);
    productRepository.saveAndFlush(product);
  }

  private String bearerToken(User user) {
    return "Bearer " + jwtTokenProvider.issueAccessToken(user);
  }
}
