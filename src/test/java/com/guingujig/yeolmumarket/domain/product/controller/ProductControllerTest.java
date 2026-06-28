package com.guingujig.yeolmumarket.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.order.entity.Order;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.review.entity.Review;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
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
class ProductControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final WishRepository wishRepository;
  private final OrderRepository orderRepository;
  private final ReviewRepository reviewRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Autowired
  ProductControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      WishRepository wishRepository,
      OrderRepository orderRepository,
      ReviewRepository reviewRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.wishRepository = wishRepository;
    this.orderRepository = orderRepository;
    this.reviewRepository = reviewRepository;
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
        .andExpect(jsonPath("$.data.content[0].wishCount").value(0))
        .andExpect(jsonPath("$.data.content[0].wished").value(false))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.content[1].productId").value(firstProduct.getId()))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 상품_목록_로그인_조회는_상품별_찜_수와_사용자_찜_여부를_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    User other = saveUser("other@example.com", "다른사용자");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);
    saveWish(viewer, secondProduct);
    saveWish(other, secondProduct);
    saveWish(other, firstProduct);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(viewer);

    mockMvc
        .perform(
            get("/api/products")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content", hasSize(2)))
        .andExpect(jsonPath("$.data.content[0].productId").value(secondProduct.getId()))
        .andExpect(jsonPath("$.data.content[0].wishCount").value(2))
        .andExpect(jsonPath("$.data.content[0].wished").value(true))
        .andExpect(jsonPath("$.data.content[1].productId").value(firstProduct.getId()))
        .andExpect(jsonPath("$.data.content[1].wishCount").value(1))
        .andExpect(jsonPath("$.data.content[1].wished").value(false));
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
        .andExpect(jsonPath("$.data.wishCount").value(0))
        .andExpect(jsonPath("$.data.wished").value(false))
        .andExpect(jsonPath("$.data.viewCount").doesNotExist())
        .andExpect(jsonPath("$.data.seller.averageRating").value(0.0));
  }

  @Test
  void 상품_상세_조회는_판매자가_받은_리뷰_평점을_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User firstBuyer = saveUser("first-buyer@example.com", "첫구매자");
    User secondBuyer = saveUser("second-buyer@example.com", "둘째구매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    Order firstOrder =
        saveOrderWithStatus(
            firstBuyer, saveProduct(seller, "거래 완료 상품 1", 10000), OrderStatus.COMPLETED);
    Order secondOrder =
        saveOrderWithStatus(
            secondBuyer, saveProduct(seller, "거래 완료 상품 2", 20000), OrderStatus.COMPLETED);
    saveReview(firstOrder, firstBuyer, seller, 5, "좋아요.");
    saveReview(secondOrder, secondBuyer, seller, 4, "괜찮아요.");

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.seller.userId").value(seller.getId()))
        .andExpect(jsonPath("$.data.seller.averageRating").value(4.5));
  }

  @Test
  void 상품_상세_로그인_조회는_찜_수와_사용자_찜_여부를_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    saveWish(viewer, product);
    saveWish(other, product);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(viewer);

    mockMvc
        .perform(
            get("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.wishCount").value(2))
        .andExpect(jsonPath("$.data.wished").value(true));
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
    Category category = saveCategory("디지털기기");
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
                      "price": 450000,
                      "categoryId": %d
                    }
                    """
                        .formatted(category.getId())))
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
    assertThat(product.getCategory().getId()).isEqualTo(category.getId());
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

  @Test
  void 상품_등록_요청에_카테고리가_없으면_400으로_응답한다() throws Exception {
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
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 상품_등록_요청의_카테고리가_null이면_400으로_응답한다() throws Exception {
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
                      "price": 450000,
                      "categoryId": null
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 존재하지_않는_카테고리로_상품을_등록하면_404로_응답한다() throws Exception {
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
                      "price": 450000,
                      "categoryId": 9223372036854775807
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  void 판매자가_자신의_상품을_수정하면_변경된_상품_정보를_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category newCategory = saveCategory("가구");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            put("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "아이패드 미니 6세대",
                      "description": "박스 포함입니다.",
                      "price": 430000,
                      "categoryId": %d
                    }
                    """
                        .formatted(newCategory.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.title").value("아이패드 미니 6세대"))
        .andExpect(jsonPath("$.data.description").value("박스 포함입니다."))
        .andExpect(jsonPath("$.data.price").value(430000))
        .andExpect(jsonPath("$.data.status").value("ON_SALE"))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(".*(Z|\\+00:00)$")));

    Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(updatedProduct.getTitle()).isEqualTo("아이패드 미니 6세대");
    assertThat(updatedProduct.getDescription()).isEqualTo("박스 포함입니다.");
    assertThat(updatedProduct.getPrice()).isEqualTo(430000);
    assertThat(updatedProduct.getCategory().getId()).isEqualTo(newCategory.getId());
  }

  @Test
  void 상품_수정은_카테고리만_포함해도_성공한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category newCategory = saveCategory("가구");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            put("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": %d
                    }
                    """
                        .formatted(newCategory.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.productId").value(product.getId()))
        .andExpect(jsonPath("$.data.title").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.price").value(450000));

    Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(updatedProduct.getCategory().getId()).isEqualTo(newCategory.getId());
  }

  @Test
  void 존재하지_않는_카테고리로_상품을_수정하면_404로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            put("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 9223372036854775807
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  void 판매자가_아닌_사용자가_상품_수정을_시도하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            put("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "아이패드 미니 6세대"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
  }

  @Test
  void 수정할_값이_없으면_400으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            put("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 판매자가_자신의_상품을_삭제하면_deleted_true를_응답하고_공개_조회에서_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            delete("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.deleted").value(true));

    Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(deletedProduct.getStatus()).isEqualTo(ProductStatus.DELETED);
    assertThat(deletedProduct.getDeletedAt()).isNotNull();

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
  void 판매자가_아닌_사용자가_상품_삭제를_시도하면_403으로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(other);

    mockMvc
        .perform(
            delete("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_ACCESS_DENIED"));
  }

  @Test
  void 거래_진행_중인_상품_삭제_시도는_409로_응답한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithStatus(seller, "예약 상품", 20000, ProductStatus.RESERVED);
    String accessToken = "Bearer " + jwtTokenProvider.issueAccessToken(seller);

    mockMvc
        .perform(
            delete("/api/products/{productId}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ACTIVE_ORDER"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private void saveWish(User user, Product product) {
    wishRepository.saveAndFlush(Wish.create(user, product));
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

  private Product saveProduct(User seller, String title, Integer price) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
  }

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "hidden", true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithDeletedAtOnly(User seller, String title, Integer price) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.of(2026, 6, 24, 0, 0));
    return productRepository.saveAndFlush(product);
  }

  private Category saveCategory(String name) {
    return categoryRepository.saveAndFlush(Category.create(name));
  }
}
