package com.guingujig.yeolmumarket.domain.wish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.dto.WishListItemResponse;
import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class WishServiceTest {

  private final WishService wishService;
  private final WishRepository wishRepository;
  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  WishServiceTest(
      WishService wishService,
      WishRepository wishRepository,
      ProductRepository productRepository,
      ProductImageRepository productImageRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JdbcTemplate jdbcTemplate) {
    this.wishService = wishService;
    this.wishRepository = wishRepository;
    this.productRepository = productRepository;
    this.productImageRepository = productImageRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jdbcTemplate = jdbcTemplate;
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
  void 찜을_생성하면_저장하고_현재_찜_수를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);

    WishResponse response = wishService.createWish(user.getId(), product.getId());

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.wished()).isTrue();
    assertThat(response.wishCount()).isEqualTo(1);
    assertThat(wishRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue();
  }

  @Test
  void 찜을_취소하면_삭제하고_현재_찜_수를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    wishService.createWish(user.getId(), product.getId());

    WishResponse response = wishService.deleteWish(user.getId(), product.getId());

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.wished()).isFalse();
    assertThat(response.wishCount()).isZero();
    assertThat(wishRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isFalse();
  }

  @Test
  void 이미_찜한_상품을_다시_찜하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    wishService.createWish(user.getId(), product.getId());

    assertThatThrownBy(() -> wishService.createWish(user.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WISH_ALREADY_EXISTS));
  }

  @Test
  void 찜하지_않은_상품을_취소하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);

    assertThatThrownBy(() -> wishService.deleteWish(user.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WISH_NOT_FOUND));
  }

  @Test
  void 숨김_상품은_찜할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    ReflectionTestUtils.setField(product, "hidden", true);
    productRepository.saveAndFlush(product);

    assertThatThrownBy(() -> wishService.createWish(user.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 삭제된_상품은_찜할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    ReflectionTestUtils.setField(product, "status", ProductStatus.DELETED);
    ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.of(2026, 6, 26, 10, 0));
    productRepository.saveAndFlush(product);

    assertThatThrownBy(() -> wishService.createWish(user.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 내_찜_목록을_최근_찜한_순서로_페이지_조회한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);
    LocalDateTime base = LocalDateTime.of(2026, 6, 27, 9, 0);
    saveWishAt(user, firstProduct, base);
    saveWishAt(user, secondProduct, base.plusMinutes(10));

    PageResponse<WishListItemResponse> response = wishService.getMyWishes(user.getId(), 0, 10);

    assertThat(response.content()).hasSize(2);
    WishListItemResponse firstItem = response.content().get(0);
    assertThat(firstItem.productId()).isEqualTo(secondProduct.getId());
    assertThat(firstItem.title()).isEqualTo("맥북 에어");
    assertThat(firstItem.price()).isEqualTo(900000);
    assertThat(firstItem.status()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(firstItem.thumbnailUrl()).isNull();
    assertThat(firstItem.wishedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(response.content().get(1).productId()).isEqualTo(firstProduct.getId());
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(10);
    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(1);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  void 내_찜_목록은_대표_이미지를_thumbnailUrl로_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    saveProductImage(
        product, "/uploads/products/%d/thumbnail.png".formatted(product.getId()), true);
    saveWishAt(user, product, LocalDateTime.of(2026, 6, 27, 9, 0));

    PageResponse<WishListItemResponse> response = wishService.getMyWishes(user.getId(), 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().thumbnailUrl())
        .isEqualTo("/uploads/products/%d/thumbnail.png".formatted(product.getId()));
  }

  @Test
  void 찜한_시각이_같으면_찜_ID_내림차순으로_정렬한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);
    LocalDateTime wishedAt = LocalDateTime.of(2026, 6, 27, 9, 0);
    Wish firstWish = saveWishAt(user, firstProduct, wishedAt);
    Wish secondWish = saveWishAt(user, secondProduct, wishedAt);

    PageResponse<WishListItemResponse> response = wishService.getMyWishes(user.getId(), 0, 10);

    assertThat(secondWish.getId()).isGreaterThan(firstWish.getId());
    assertThat(response.content())
        .extracting(WishListItemResponse::productId)
        .containsExactly(secondProduct.getId(), firstProduct.getId());
  }

  @Test
  void 내_찜_목록은_다른_사용자의_찜을_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    User otherUser = saveUser("other@example.com", "다른유저");
    Product myProduct = saveProduct(seller, "내가 찜한 상품", 10000);
    Product otherProduct = saveProduct(seller, "다른 사용자가 찜한 상품", 20000);
    LocalDateTime wishedAt = LocalDateTime.of(2026, 6, 27, 9, 0);
    saveWishAt(user, myProduct, wishedAt);
    saveWishAt(otherUser, otherProduct, wishedAt.plusMinutes(1));

    PageResponse<WishListItemResponse> response = wishService.getMyWishes(user.getId(), 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).productId()).isEqualTo(myProduct.getId());
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 내_찜_목록은_숨김_상품과_삭제_상품을_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product publicProduct = saveProduct(seller, "공개 상품", 10000);
    Product hiddenProduct = saveProduct(seller, "숨김 상품", 20000);
    Product deletedProduct = saveProduct(seller, "삭제 상품", 30000);
    ReflectionTestUtils.setField(hiddenProduct, "hidden", true);
    ReflectionTestUtils.setField(deletedProduct, "status", ProductStatus.DELETED);
    ReflectionTestUtils.setField(deletedProduct, "deletedAt", LocalDateTime.of(2026, 6, 27, 10, 0));
    productRepository.saveAndFlush(hiddenProduct);
    productRepository.saveAndFlush(deletedProduct);
    LocalDateTime wishedAt = LocalDateTime.of(2026, 6, 27, 9, 0);
    saveWishAt(user, publicProduct, wishedAt);
    saveWishAt(user, hiddenProduct, wishedAt.plusMinutes(1));
    saveWishAt(user, deletedProduct, wishedAt.plusMinutes(2));

    PageResponse<WishListItemResponse> response = wishService.getMyWishes(user.getId(), 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).productId()).isEqualTo(publicProduct.getId());
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void 내_찜_목록_잘못된_페이지_요청은_INVALID_PAGINATION을_반환한다() {
    assertThatThrownBy(() -> wishService.getMyWishes(1L, -1, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
    assertThatThrownBy(() -> wishService.getMyWishes(1L, 0, 0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
    assertThatThrownBy(() -> wishService.getMyWishes(1L, 0, 101))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  private void deleteAll() {
    wishRepository.deleteAll();
    productImageRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private Product saveProduct(User seller) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
  }

  private Product saveProduct(User seller, String title, Integer price) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, title, "생활기스", price);
  }

  private Wish saveWishAt(User user, Product product, LocalDateTime wishedAt) {
    Wish wish = wishRepository.saveAndFlush(Wish.create(user, product));
    jdbcTemplate.update(
        "update wish set created_at = ? where id = ?",
        wishedAt.toString().replace('T', ' '),
        wish.getId());
    return wish;
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private ProductImage saveProductImage(Product product, String url, boolean thumbnail) {
    return productImageRepository.saveAndFlush(ProductImage.create(product, url, thumbnail));
  }
}
