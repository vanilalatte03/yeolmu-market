package com.guingujig.yeolmumarket.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.dto.CategoryProductListItemResponse;
import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
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
class CategoryFacadeTest {

  private final CategoryFacade categoryFacade;
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  CategoryFacadeTest(
      CategoryFacade categoryFacade,
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      ProductImageRepository productImageRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.categoryFacade = categoryFacade;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.productImageRepository = productImageRepository;
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
  void 카테고리별_상품_조회는_해당_카테고리의_공개_상품만_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category category = saveCategory("디지털기기");
    Category otherCategory = saveCategory("가구");
    Product onSaleProduct = saveProduct(seller, category, "아이패드 미니 6", 450000);
    Product reservedProduct =
        saveProductWithStatus(seller, category, "예약 상품", 20000, ProductStatus.RESERVED);
    saveProduct(seller, otherCategory, "다른 카테고리 상품", 30000);
    saveHiddenProduct(seller, category, "숨김 상품", 40000);
    saveProductWithStatus(seller, category, "삭제 상태 상품", 50000, ProductStatus.DELETED);
    saveProductWithDeletedAtOnly(seller, category, "삭제일 설정 상품", 60000);

    PageResponse<CategoryProductListItemResponse> response =
        categoryFacade.getCategoryProducts(category.getId(), 0, 10, "latest");

    assertThat(response.content())
        .extracting(CategoryProductListItemResponse::productId)
        .containsExactly(reservedProduct.getId(), onSaleProduct.getId());
    assertThat(response.content())
        .extracting(CategoryProductListItemResponse::status)
        .containsExactly(ProductStatus.RESERVED, ProductStatus.ON_SALE);
    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  void 카테고리별_상품_조회는_대표_이미지를_thumbnailUrl로_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category category = saveCategory("디지털기기");
    Product product = saveProduct(seller, category, "아이패드 미니 6", 450000);
    saveProductImage(product, "/uploads/products/%d/thumbnail.png".formatted(product.getId()));

    PageResponse<CategoryProductListItemResponse> response =
        categoryFacade.getCategoryProducts(category.getId(), 0, 10, "latest");

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().thumbnailUrl())
        .isEqualTo("/uploads/products/%d/thumbnail.png".formatted(product.getId()));
  }

  @Test
  void 카테고리별_상품_조회는_정렬_조건을_지원한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category category = saveCategory("디지털기기");
    Product firstProduct = saveProduct(seller, category, "중간가 상품", 20000);
    Product secondProduct = saveProduct(seller, category, "저가 상품", 10000);
    Product thirdProduct = saveProduct(seller, category, "고가 상품", 30000);

    PageResponse<CategoryProductListItemResponse> latest =
        categoryFacade.getCategoryProducts(category.getId(), 0, 10, "latest");
    PageResponse<CategoryProductListItemResponse> priceAsc =
        categoryFacade.getCategoryProducts(category.getId(), 0, 10, "priceAsc");
    PageResponse<CategoryProductListItemResponse> priceDesc =
        categoryFacade.getCategoryProducts(category.getId(), 0, 10, "priceDesc");

    assertThat(latest.content())
        .extracting(CategoryProductListItemResponse::productId)
        .containsExactly(thirdProduct.getId(), secondProduct.getId(), firstProduct.getId());
    assertThat(priceAsc.content())
        .extracting(CategoryProductListItemResponse::price)
        .containsExactly(10000, 20000, 30000);
    assertThat(priceDesc.content())
        .extracting(CategoryProductListItemResponse::price)
        .containsExactly(30000, 20000, 10000);
  }

  @Test
  void 존재하지_않는_카테고리의_상품_조회는_실패한다() {
    assertThatThrownBy(() -> categoryFacade.getCategoryProducts(Long.MAX_VALUE, 0, 10, "latest"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
  }

  @Test
  void 카테고리별_상품_조회_페이지_조건이_잘못되면_실패한다() {
    Category category = saveCategory("디지털기기");

    assertThatThrownBy(() -> categoryFacade.getCategoryProducts(category.getId(), -1, 10, "latest"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
    assertThatThrownBy(() -> categoryFacade.getCategoryProducts(category.getId(), 0, 0, "latest"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
    assertThatThrownBy(() -> categoryFacade.getCategoryProducts(category.getId(), 0, 101, "latest"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
  }

  @Test
  void 카테고리별_상품_조회_지원하지_않는_정렬은_실패한다() {
    Category category = saveCategory("디지털기기");

    assertThatThrownBy(() -> categoryFacade.getCategoryProducts(category.getId(), 0, 10, "oldest"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  private void deleteAll() {
    productImageRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private Category saveCategory(String name) {
    return categoryRepository.saveAndFlush(Category.create(name));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, Category category, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price, category);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, Category category, String title, Integer price, ProductStatus status) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price, category);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private Product saveHiddenProduct(User seller, Category category, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price, category);
    product.changeHidden(true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithDeletedAtOnly(
      User seller, Category category, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price, category);
    ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.of(2026, 6, 24, 0, 0));
    return productRepository.saveAndFlush(product);
  }

  private ProductImage saveProductImage(Product product, String url) {
    return productImageRepository.saveAndFlush(ProductImage.create(product, url, true));
  }
}
