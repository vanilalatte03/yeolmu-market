package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class SearchServiceTest {

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
