package com.guingujig.yeolmumarket.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  ProductControllerTest(
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
  void 상품_목록_조회에_성공하면_공개_상품을_페이지로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);

    mockMvc
        .perform(get("/api/products").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].productId").value(secondProduct.getId()))
        .andExpect(jsonPath("$.data.content[0].title").value("맥북 에어"))
        .andExpect(jsonPath("$.data.content[0].price").value(900000))
        .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.content[0].sellerNickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.content[1].productId").value(firstProduct.getId()))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 상품_목록_조회는_숨김_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveHiddenProduct(seller, "숨김 상품", 20000);

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 상품_목록_조회는_삭제_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveProductWithStatus(seller, "삭제 상품", 20000, ProductStatus.DELETED);

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 상품_목록_조회는_삭제일만_설정된_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", 10000);
    saveProductWithDeletedAtOnly(seller, "삭제일 설정 상품", 20000);

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 상품"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 상품_상세_조회에_성공하면_공개_상품_상세를_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.title").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.description").value("생활기스 조금 있습니다."))
        .andExpect(jsonPath("$.data.price").value(450000))
        .andExpect(jsonPath("$.data.status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.seller.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.seller.nickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.category").doesNotExist())
        .andExpect(jsonPath("$.data.images").doesNotExist())
        .andExpect(jsonPath("$.data.wishCount").doesNotExist())
        .andExpect(jsonPath("$.data.wished").doesNotExist())
        .andExpect(jsonPath("$.data.viewCount").doesNotExist())
        .andExpect(jsonPath("$.data.seller.averageRating").doesNotExist());
  }

  @Test
  void 존재하지_않는_상품_상세_조회는_404로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/products/{productId}", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 숨김_상품_상세_조회는_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveHiddenProduct(seller, "숨김 상품", 20000);

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 삭제_상품_상세_조회는_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithStatus(seller, "삭제 상품", 20000, ProductStatus.DELETED);

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 삭제일만_설정된_상품_상세_조회는_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithDeletedAtOnly(seller, "삭제일 설정 상품", 20000);

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void 잘못된_페이지네이션_요청은_400으로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/products").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void 허용하지_않는_상품_상태_요청은_400으로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/products").param("status", "DELETED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ENUM_VALUE"));
  }

  @Test
  void 상품_등록에_성공하면_상품이_생성되고_201로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "아이패드 미니 6",
                      "description": "생활기스 조금 있습니다.",
                      "price": 450000
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").isNumber())
        .andExpect(jsonPath("$.data.title").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.description").value("생활기스 조금 있습니다."))
        .andExpect(jsonPath("$.data.price").value(450000))
        .andExpect(jsonPath("$.data.status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.seller.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.seller.nickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.createdAt", matchesPattern(".*(Z|\\+00:00)$")));

    Product product = productRepository.findAll().getFirst();
    assertThat(product.getSeller().getId()).isEqualTo(seller.getId());
    assertThat(product.getTitle()).isEqualTo("아이패드 미니 6");
    assertThat(product.getDescription()).isEqualTo("생활기스 조금 있습니다.");
    assertThat(product.getPrice()).isEqualTo(450000);
    assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(product.isHidden()).isFalse();
    assertThat(product.getCategory()).isNull();
  }

  @Test
  void 인증되지_않은_사용자가_상품을_등록하면_401로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "아이패드 미니 6",
                      "description": "생활기스 조금 있습니다.",
                      "price": 450000
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 상품_등록_요청값_검증에_실패하면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "",
                      "description": "",
                      "price": 0
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithDeletedAtOnly(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.of(2026, 6, 24, 0, 0));
    return productRepository.saveAndFlush(product);
  }
}
