package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.domain.search.repository.PopularKeywordRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SearchServiceRedisCacheFallbackTest {

  @MockitoBean private PopularKeywordRepository popularKeywordRepository;

  @MockitoBean private LettuceConnectionFactory redisConnectionFactory;

  private final SearchService searchService;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  SearchServiceRedisCacheFallbackTest(
      SearchService searchService,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.searchService = searchService;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
    doThrow(new RedisConnectionFailureException("redis unavailable"))
        .when(redisConnectionFactory)
        .getConnection();
  }

  @AfterEach
  void tearDown() {
    deleteAll();
  }

  @Test
  void Redis_캐시가_장애여도_v2_상품_검색은_MySQL_조회로_동작한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProductsV2(
            new SearchProductRequest("아이패드", null, null, ProductStatus.ON_SALE, 0, 10, "latest"));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("아이패드 미니 6세대");
    assertThat(response.totalElements()).isEqualTo(1);
    verify(popularKeywordRepository).incrementSearchCount("아이패드");
  }

  private void deleteAll() {
    orderRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, String description, Integer price) {
    Product product = Product.create(seller, title, description, price);
    return productRepository.saveAndFlush(product);
  }
}
