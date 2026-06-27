package com.guingujig.yeolmumarket.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.repository.OrderRepository;
import com.guingujig.yeolmumarket.domain.order.service.OrderService;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.product.service.ProductService;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductRequest;
import com.guingujig.yeolmumarket.domain.search.dto.SearchProductResponse;
import com.guingujig.yeolmumarket.domain.search.repository.PopularKeywordRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.config.CacheConfig;
import com.guingujig.yeolmumarket.global.config.SearchCacheProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class SearchServiceTest {

  @MockitoBean private PopularKeywordRepository popularKeywordRepository;

  private final SearchService searchService;
  private final ProductService productService;
  private final OrderService orderService;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final UserRepository userRepository;
  private final WishRepository wishRepository;
  private final PasswordEncoder passwordEncoder;
  private final CacheManager cacheManager;
  private final SearchCacheProperties searchCacheProperties;

  @Autowired
  SearchServiceTest(
      SearchService searchService,
      ProductService productService,
      OrderService orderService,
      ProductRepository productRepository,
      OrderRepository orderRepository,
      UserRepository userRepository,
      WishRepository wishRepository,
      PasswordEncoder passwordEncoder,
      CacheManager cacheManager,
      SearchCacheProperties searchCacheProperties) {
    this.searchService = searchService;
    this.productService = productService;
    this.orderService = orderService;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
    this.wishRepository = wishRepository;
    this.passwordEncoder = passwordEncoder;
    this.cacheManager = cacheManager;
    this.searchCacheProperties = searchCacheProperties;
  }

  @BeforeEach
  void setUp() {
    deleteAll();
    clearSearchCache();
  }

  @AfterEach
  void tearDown() {
    clearSearchCache();
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
  void 상품_검색은_인증_사용자_기준_찜_정보를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);
    saveWish(viewer, product);
    saveWish(other, product);

    PageResponse<SearchProductResponse> response =
        searchService.searchProducts(
            request("아이패드", null, null, ProductStatus.ON_SALE), viewer.getId());

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().wishCount()).isEqualTo(2);
    assertThat(response.content().getFirst().wished()).isTrue();
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

  @Test
  void v2_상품_검색은_v1과_동일한_결과를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "생활기스 조금 있습니다.", 430000);
    saveProduct(seller, "무선 키보드", "아이패드 호환 모델입니다.", 50000);
    saveProduct(seller, "맥북 에어", "깨끗합니다.", 900000);
    SearchProductRequest request =
        new SearchProductRequest("아이패드", 10000, 500000, ProductStatus.ON_SALE, 0, 10, "priceAsc");

    PageResponse<SearchProductResponse> v1Response = searchService.searchProducts(request);
    PageResponse<SearchProductResponse> v2Response = searchService.searchProductsV2(request);

    assertThat(v2Response).isEqualTo(v1Response);
  }

  @Test
  void v2_동일_검색_조건은_반복_조회시_캐시_결과를_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "설명", 430000);
    SearchProductRequest request = request("아이패드", null, null, ProductStatus.ON_SALE);

    PageResponse<SearchProductResponse> firstResponse = searchService.searchProductsV2(request);
    saveProduct(seller, "아이패드 프로", "설명", 900000);
    PageResponse<SearchProductResponse> secondResponse = searchService.searchProductsV2(request);

    assertThat(firstResponse.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("아이패드 미니 6세대");
    assertThat(secondResponse).isEqualTo(firstResponse);
  }

  @Test
  void v2_캐시_hit_상황에서도_사용자별_찜_여부는_섞이지_않는다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", "설명", 430000);
    saveWish(viewer, product);
    SearchProductRequest request = request("아이패드", null, null, ProductStatus.ON_SALE);

    PageResponse<SearchProductResponse> viewerResponse =
        searchService.searchProductsV2(request, viewer.getId());
    PageResponse<SearchProductResponse> otherResponse =
        searchService.searchProductsV2(request, other.getId());

    assertThat(viewerResponse.content()).hasSize(1);
    assertThat(viewerResponse.content().getFirst().wishCount()).isEqualTo(1);
    assertThat(viewerResponse.content().getFirst().wished()).isTrue();
    assertThat(otherResponse.content()).hasSize(1);
    assertThat(otherResponse.content().getFirst().wishCount()).isEqualTo(1);
    assertThat(otherResponse.content().getFirst().wished()).isFalse();
  }

  @Test
  void 찜_변경_후_v2_캐시_hit_상황에서도_찜_정보는_최신_상태를_반영한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    Product product = saveProduct(seller, "아이패드 미니 6세대", "설명", 430000);
    SearchProductRequest request = request("아이패드", null, null, ProductStatus.ON_SALE);

    PageResponse<SearchProductResponse> beforeResponse =
        searchService.searchProductsV2(request, viewer.getId());
    saveWish(viewer, product);
    PageResponse<SearchProductResponse> afterResponse =
        searchService.searchProductsV2(request, viewer.getId());

    assertThat(beforeResponse.content()).hasSize(1);
    assertThat(beforeResponse.content().getFirst().wishCount()).isZero();
    assertThat(beforeResponse.content().getFirst().wished()).isFalse();
    assertThat(afterResponse.content()).hasSize(1);
    assertThat(afterResponse.content().getFirst().wishCount()).isEqualTo(1);
    assertThat(afterResponse.content().getFirst().wished()).isTrue();
  }

  @Test
  void v2_캐시_hit_상황에서도_인기_검색어는_요청마다_집계한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "설명", 430000);
    SearchProductRequest request = request("아이패드", null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(request);
    searchService.searchProductsV2(request);

    verify(popularKeywordRepository, times(2)).incrementSearchCount("아이패드");
  }

  @Test
  void v2_키워드_trim과_기본값이_반영된_정규화_캐시_key를_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "설명", 430000);

    PageResponse<SearchProductResponse> firstResponse =
        searchService.searchProductsV2(
            new SearchProductRequest("  아이패드  ", null, null, null, 0, 10, null));
    saveProduct(seller, "아이패드 프로", "설명", 900000);
    PageResponse<SearchProductResponse> secondResponse =
        searchService.searchProductsV2(
            new SearchProductRequest("아이패드", null, null, ProductStatus.ON_SALE, 0, 10, "latest"));

    assertThat(secondResponse).isEqualTo(firstResponse);
  }

  @Test
  void v2_blank_키워드는_null_키워드와_같은_캐시_key를_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "아이패드 미니 6세대", "설명", 430000);

    PageResponse<SearchProductResponse> firstResponse =
        searchService.searchProductsV2(
            new SearchProductRequest("   ", null, null, ProductStatus.ON_SALE, 0, 10, "latest"));
    saveProduct(seller, "맥북 에어", "설명", 900000);
    PageResponse<SearchProductResponse> secondResponse =
        searchService.searchProductsV2(
            new SearchProductRequest(null, null, null, ProductStatus.ON_SALE, 0, 10, "latest"));

    assertThat(secondResponse).isEqualTo(firstResponse);
  }

  @Test
  void v2_검색_조건이_다르면_서로_다른_캐시_key를_사용한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "저가 상품", "설명", 10000);
    SearchProductRequest latestRequest =
        new SearchProductRequest(null, null, null, ProductStatus.ON_SALE, 0, 10, "latest");

    searchService.searchProductsV2(latestRequest);
    saveProduct(seller, "고가 상품", "설명", 900000);
    saveProduct(seller, "중간 상품", "설명", 20000);
    saveProductWithStatus(seller, "예약 상품", "설명", 50000, ProductStatus.RESERVED);
    PageResponse<SearchProductResponse> differentKeywordResponse =
        searchService.searchProductsV2(
            new SearchProductRequest("고가", null, null, ProductStatus.ON_SALE, 0, 10, "latest"));
    PageResponse<SearchProductResponse> differentSortResponse =
        searchService.searchProductsV2(
            new SearchProductRequest(null, null, null, ProductStatus.ON_SALE, 0, 10, "priceDesc"));
    PageResponse<SearchProductResponse> differentMinPriceResponse =
        searchService.searchProductsV2(
            new SearchProductRequest(null, 500000, null, ProductStatus.ON_SALE, 0, 10, "latest"));
    PageResponse<SearchProductResponse> differentMaxPriceResponse =
        searchService.searchProductsV2(
            new SearchProductRequest(null, null, 500000, ProductStatus.ON_SALE, 0, 10, "latest"));
    PageResponse<SearchProductResponse> differentStatusResponse =
        searchService.searchProductsV2(
            new SearchProductRequest(null, null, null, ProductStatus.RESERVED, 0, 10, "latest"));
    PageResponse<SearchProductResponse> differentPageResponse =
        searchService.searchProductsV2(
            new SearchProductRequest(null, null, null, ProductStatus.ON_SALE, 1, 1, "latest"));

    assertThat(differentKeywordResponse.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("고가 상품");
    assertThat(differentSortResponse.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("고가 상품", "중간 상품", "저가 상품");
    assertThat(differentMinPriceResponse.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("고가 상품");
    assertThat(differentMaxPriceResponse.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("중간 상품", "저가 상품");
    assertThat(differentStatusResponse.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("예약 상품");
    assertThat(differentPageResponse.page()).isEqualTo(1);
    assertThat(differentPageResponse.size()).isEqualTo(1);
    assertThat(differentPageResponse.totalElements()).isEqualTo(3);
  }

  @Test
  void 상품_등록_후_v2_검색_캐시가_무효화되어_새_상품이_반영된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    saveProduct(seller, "기존 상품", "설명", 10000);
    SearchProductRequest request = request(null, null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(request);
    productService.createProduct(seller.getId(), new CreateProductRequest("새 상품", "설명", 20000));
    PageResponse<SearchProductResponse> response = searchService.searchProductsV2(request);

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("새 상품", "기존 상품");
  }

  @Test
  void 상품_수정_후_v2_검색_캐시가_무효화되어_변경값이_반영된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "변경 전 상품", "설명", 10000);
    SearchProductRequest request = request(null, null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(request);
    productService.updateProduct(
        seller.getId(), product.getId(), new UpdateProductRequest("변경 후 상품", "수정 설명", 20000));
    PageResponse<SearchProductResponse> response = searchService.searchProductsV2(request);

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("변경 후 상품");
    assertThat(response.content()).extracting(SearchProductResponse::price).containsExactly(20000);
  }

  @Test
  void 상품_삭제_후_v2_검색_캐시가_무효화되어_검색에서_제외된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "삭제 대상 상품", "설명", 10000);
    SearchProductRequest request = request(null, null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(request);
    productService.deleteProduct(seller.getId(), product.getId());
    PageResponse<SearchProductResponse> response = searchService.searchProductsV2(request);

    assertThat(response.content()).isEmpty();
  }

  @Test
  void 상품_숨김_변경_후_v2_검색_캐시가_무효화되어_검색에서_제외된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "숨김 대상 상품", "설명", 10000);
    SearchProductRequest request = request(null, null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(request);
    productService.updateProductHiddenStatus(
        product.getId(), new UpdateProductHiddenStatusRequest(true));
    PageResponse<SearchProductResponse> response = searchService.searchProductsV2(request);

    assertThat(response.content()).isEmpty();
  }

  @Test
  void 주문_생성_후_v2_검색_캐시가_무효화되어_예약_상태가_반영된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "예약 대상 상품", "설명", 10000);
    SearchProductRequest onSaleRequest = request(null, null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(onSaleRequest);
    orderService.createOrder(buyer.getId(), product.getId());
    PageResponse<SearchProductResponse> onSaleResponse =
        searchService.searchProductsV2(onSaleRequest);
    PageResponse<SearchProductResponse> reservedResponse =
        searchService.searchProductsV2(request(null, null, null, ProductStatus.RESERVED));

    assertThat(onSaleResponse.content()).isEmpty();
    assertThat(reservedResponse.content())
        .extracting(SearchProductResponse::status)
        .containsExactly(ProductStatus.RESERVED);
  }

  @Test
  void 주문_취소_후_v2_검색_캐시가_무효화되어_판매중_상태가_반영된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User buyer = saveUser("buyer@example.com", "열무구매자");
    Product product = saveProduct(seller, "예약 취소 상품", "설명", 10000);
    CreateOrderResponse createdOrder = orderService.createOrder(buyer.getId(), product.getId());
    SearchProductRequest onSaleRequest = request(null, null, null, ProductStatus.ON_SALE);

    searchService.searchProductsV2(onSaleRequest);
    orderService.cancelOrder(buyer.getId(), createdOrder.orderId());
    PageResponse<SearchProductResponse> response = searchService.searchProductsV2(onSaleRequest);

    assertThat(response.content())
        .extracting(SearchProductResponse::title)
        .containsExactly("예약 취소 상품");
  }

  @Test
  void Redis_기반_CacheManager가_검색_캐시_TTL과_serializer를_적용한다() {
    RedisCacheManager redisCacheManager =
        (RedisCacheManager)
            new CacheConfig()
                .cacheManager(
                    org.mockito.Mockito.mock(RedisConnectionFactory.class), searchCacheProperties);
    redisCacheManager.afterPropertiesSet();
    assertThat(redisCacheManager.getCacheNames())
        .containsExactly(SearchCacheNames.PRODUCT_SEARCH_V2);

    RedisCache cache = (RedisCache) redisCacheManager.getCache(SearchCacheNames.PRODUCT_SEARCH_V2);
    assertThat(cache).isNotNull();
    RedisCacheConfiguration configuration = cache.getCacheConfiguration();

    assertThat(searchCacheProperties.productsV2().ttl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(configuration.getTtlFunction().getTimeToLive("key", "value"))
        .isEqualTo(Duration.ofMinutes(5));
    assertThat(configuration.getKeyPrefixFor(SearchCacheNames.PRODUCT_SEARCH_V2))
        .isEqualTo("cache:search:products:v2::");

    byte[] keyBytes = toBytes(configuration.getKeySerializationPair().write("search-key"));
    assertThat(new String(keyBytes, StandardCharsets.UTF_8)).isEqualTo("search-key");

    PageResponse<SearchProductResponse> pageResponse =
        new PageResponse<>(
            List.of(
                new SearchProductResponse(
                    1L,
                    "아이패드",
                    430000,
                    ProductStatus.ON_SALE,
                    null,
                    "열무판매자",
                    0,
                    false,
                    OffsetDateTime.of(2026, 6, 26, 0, 0, 0, 0, ZoneOffset.UTC))),
            0,
            10,
            1,
            1,
            false);
    ByteBuffer valueBuffer = configuration.getValueSerializationPair().write(pageResponse);
    Object deserialized = configuration.getValueSerializationPair().read(valueBuffer);

    assertThat(deserialized).isEqualTo(pageResponse);
  }

  @TestConfiguration
  static class TestCacheConfig {

    @Bean
    @Primary
    CacheManager testCacheManager() {
      return new ConcurrentMapCacheManager(SearchCacheNames.PRODUCT_SEARCH_V2);
    }
  }

  private SearchProductRequest request(
      String keyword, Integer minPrice, Integer maxPrice, ProductStatus status) {
    return new SearchProductRequest(keyword, minPrice, maxPrice, status, 0, 10, "latest");
  }

  private void deleteAll() {
    orderRepository.deleteAll();
    wishRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();
  }

  private void clearSearchCache() {
    org.springframework.cache.Cache cache =
        cacheManager.getCache(SearchCacheNames.PRODUCT_SEARCH_V2);
    if (cache != null) {
      cache.invalidate();
    }
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, String description, Integer price) {
    Product product = Product.create(seller, title, description, price);
    return productRepository.saveAndFlush(product);
  }

  private void saveWish(User user, Product product) {
    wishRepository.saveAndFlush(Wish.create(user, product));
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

  private byte[] toBytes(ByteBuffer byteBuffer) {
    byte[] bytes = new byte[byteBuffer.remaining()];
    byteBuffer.get(bytes);
    return bytes;
  }
}
