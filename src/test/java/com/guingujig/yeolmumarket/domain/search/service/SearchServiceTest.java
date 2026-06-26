package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.domain.search.repository.PopularKeywordRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class SearchServiceTest {

  @MockitoBean private PopularKeywordRepository popularKeywordRepository;

  private final SearchService searchService;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  SearchServiceTest(
      SearchService searchService,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.searchService = searchService;
    this.productRepository = productRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
  }

  @AfterEach
  void tearDown() {
    deleteAll();
  }

  @Test
  void 키워드로_상품명과_설명에_매칭되는_상품을_검색한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);
    saveProduct(seller, "무선 키보드", "아이패드 호환 모델입니다.", 50000);
    saveProduct(seller, "맥북 에어", "깨끗합니다.", 900000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request("아이패드", null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactlyInAnyOrder("아이패드 미니 6세대", "무선 키보드");
    assertThat(response.totalElements()).isEqualTo(2);
  }

  @Test
  void 퍼센트_키워드는_LIKE_와일드카드가_아닌_문자로_검색한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "정가 50% 할인", "새 상품입니다.", 10000);
    saveProduct(seller, "일반 상품", "할인 안내", 20000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request("%", null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("정가 50% 할인");
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 키워드_검색시_집계용_키워드는_trim만_적용하고_LIKE_escape는_적용하지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "정가 50% 할인", "새 상품입니다.", 10000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request("  50%  ", null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("정가 50% 할인");
    verify(popularKeywordRepository).incrementSearchCount("50%");
  }

  @Test
  void 빈_키워드는_인기_검색어로_집계하지_않는다() {
    searchService.searchProducts(request("   ", null, null, ProductStatus.ON_SALE));

    verify(popularKeywordRepository, never())
        .incrementSearchCount(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void Redis_집계가_실패해도_상품_검색_결과를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);
    doThrow(new RedisConnectionFailureException("redis unavailable"))
        .when(popularKeywordRepository)
        .incrementSearchCount("아이패드");

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request("아이패드", null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("아이패드 미니 6세대");
    verify(popularKeywordRepository).incrementSearchCount("아이패드");
  }

  @Test
  void 언더스코어_키워드는_LIKE_와일드카드가_아닌_문자로_검색한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "model_a 키보드", "깨끗합니다.", 10000);
    saveProduct(seller, "model-a 마우스", "깨끗합니다.", 20000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request("_", null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("model_a 키보드");
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 가격_범위로_상품을_검색한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "저가 상품", "설명", 10000);
    saveProduct(seller, "중간 상품", "설명", 50000);
    saveProduct(seller, "고가 상품", "설명", 100000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(
            new SearchProductRequest(null, 30000, 80000, ProductStatus.ON_SALE, 0, 10, "priceAsc"));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("중간 상품");
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 상품_상태로_상품을_검색한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "판매 중 상품", "설명", 10000);
    saveProductWithStatus(seller, "예약 상품", "설명", 20000, ProductStatus.RESERVED);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request(null, null, null, ProductStatus.RESERVED));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("예약 상품");
    assertThat(response.content().getFirst().status()).isEqualTo(ProductStatus.RESERVED);
  }

  @Test
  void 숨김_상품은_검색에서_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", "설명", 10000);
    saveHiddenProduct(seller, "숨김 상품", "설명", 20000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request(null, null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("공개 상품");
  }

  @Test
  void 삭제_상품은_검색에서_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "공개 상품", "설명", 10000);
    saveProductWithStatus(seller, "삭제 상태 상품", "설명", 20000, ProductStatus.DELETED);
    saveProductWithDeletedAtOnly(seller, "삭제일 상품", "설명", 30000);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request(null, null, null, ProductStatus.ON_SALE));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("공개 상품");
  }

  @Test
  void 상태가_없으면_ON_SALE로_검색한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "판매 중 상품", "설명", 10000);
    saveProductWithStatus(seller, "예약 상품", "설명", 20000, ProductStatus.RESERVED);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(request(null, null, null, null));

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("판매 중 상품");
  }

  @Test
  void 잘못된_가격_범위는_실패한다() {
    assertThatThrownBy(
            () -> searchService.searchProducts(request(null, 5000, 1000, ProductStatus.ON_SALE)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void 잘못된_페이지_요청은_실패한다() {
    assertThatThrownBy(
            () ->
                searchService.searchProducts(
                    new SearchProductRequest(
                        null, null, null, ProductStatus.ON_SALE, -1, 10, "latest")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  @Test
  void 삭제_상태_검색은_실패한다() {
    assertThatThrownBy(
            () -> searchService.searchProducts(request(null, null, null, ProductStatus.DELETED)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ENUM_VALUE));
  }

  private SearchProductRequest request(
      String keyword, Integer minPrice, Integer maxPrice, ProductStatus status) {
    return new SearchProductRequest(keyword, minPrice, maxPrice, status, 0, 10, "latest");
  }

  private void deleteAll() {
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

  private Product saveProductWithStatus(
      User seller, String title, String description, Integer price, ProductStatus status) {
    Product product = Product.create(seller, title, description, price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private Product saveHiddenProduct(User seller, String title, String description, Integer price) {
    Product product = Product.create(seller, title, description, price);
    ReflectionTestUtils.setField(product, "hidden", true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithDeletedAtOnly(
      User seller, String title, String description, Integer price) {
    Product product = Product.create(seller, title, description, price);
    ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.of(2026, 6, 24, 0, 0));
    return productRepository.saveAndFlush(product);
  }
}
