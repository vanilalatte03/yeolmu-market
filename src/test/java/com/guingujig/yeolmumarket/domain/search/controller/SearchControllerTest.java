package com.guingujig.yeolmumarket.domain.search.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.auth.repository.RevokedAccessTokenRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.dto.PopularKeyword;
import com.guingujig.yeolmumarket.domain.search.repository.PopularKeywordRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchControllerTest {

  @MockitoBean private RevokedAccessTokenRepository revokedAccessTokenRepository;
  @MockitoBean private PopularKeywordRepository popularKeywordRepository;

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  SearchControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      PasswordEncoder passwordEncoder) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Test
  void 비회원이_상품_검색에_성공하면_공통_페이지_응답을_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);
    saveProduct(seller, "맥북 에어", "깨끗합니다.", 900000);

    mockMvc
        .perform(
            get("/api/search/products")
                .param("keyword", "아이패드")
                .param("minPrice", "400000")
                .param("maxPrice", "500000")
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
        .andExpect(jsonPath("$.data.content[0].sellerNickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 비회원이_v2_상품_검색에_성공하면_v1과_동일한_공통_페이지_응답을_반환한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);
    saveProduct(seller, "맥북 에어", "깨끗합니다.", 900000);

    mockMvc
        .perform(
            get("/api/search/v2/products")
                .param("keyword", "아이패드")
                .param("minPrice", "400000")
                .param("maxPrice", "500000")
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
        .andExpect(jsonPath("$.data.content[0].sellerNickname").value("열무판매자"))
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(".*(Z|\\+00:00)$")))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void 잘못된_가격_범위는_400_VALIDATION_FAILED로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/search/products").param("minPrice", "5000").param("maxPrice", "1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 잘못된_페이지_요청은_400_INVALID_PAGINATION으로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/search/products").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
  }

  @Test
  void 허용하지_않는_상품_상태는_400_INVALID_ENUM_VALUE로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/search/products").param("status", "DELETED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ENUM_VALUE"));
  }

  @Test
  void 비회원이_인기_검색어_조회에_성공하면_rank와_검색횟수를_반환한다() throws Exception {
    when(popularKeywordRepository.findTopKeywords(2))
        .thenReturn(List.of(new PopularKeyword("아이패드", 3), new PopularKeyword("맥북", 2)));

    mockMvc
        .perform(get("/api/search/popular-keywords").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.keywords", hasSize(2)))
        .andExpect(jsonPath("$.data.keywords[0].rank").value(1))
        .andExpect(jsonPath("$.data.keywords[0].keyword").value("아이패드"))
        .andExpect(jsonPath("$.data.keywords[0].searchCount").value(3))
        .andExpect(jsonPath("$.data.keywords[1].rank").value(2));
  }

  @Test
  void 인기_검색어_limit_범위가_잘못되면_400_VALIDATION_FAILED로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/search/popular-keywords").param("limit", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 인기_검색어_Redis_조회가_실패하면_503_REDIS_UNAVAILABLE로_응답한다() throws Exception {
    when(popularKeywordRepository.findTopKeywords(10))
        .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

    mockMvc
        .perform(get("/api/search/popular-keywords"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REDIS_UNAVAILABLE"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, String description, Integer price) {
    Product product = Product.create(seller, title, description, price);
    return productRepository.saveAndFlush(product);
  }
}
