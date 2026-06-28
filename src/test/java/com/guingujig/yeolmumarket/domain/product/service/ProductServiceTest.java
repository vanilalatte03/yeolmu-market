package com.guingujig.yeolmumarket.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.dto.AdminHiddenProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.CreateProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductDetailResponse;
import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductImage;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.support.ProductTestFactory;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ProductServiceTest {

  private final ProductService productService;
  private final ProductRepository productRepository;
  private final ProductImageRepository productImageRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final WishRepository wishRepository;
  private final PasswordEncoder passwordEncoder;

  private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 6, 24, 10, 0);

  @Autowired
  ProductServiceTest(
      ProductService productService,
      ProductRepository productRepository,
      ProductImageRepository productImageRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      WishRepository wishRepository,
      PasswordEncoder passwordEncoder) {
    this.productService = productService;
    this.productRepository = productRepository;
    this.productImageRepository = productImageRepository;
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
    this.wishRepository = wishRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Test
  void 숨김_상품_목록은_숨김_상품만_반환하고_삭제된_상품은_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product hiddenProduct = saveHiddenProduct(seller, "숨김 상품", 20000);
    saveProduct(seller, "공개 상품", 30000);
    saveDeletedHiddenProduct(seller, "삭제 숨김 상품", 40000);

    PageResponse<AdminHiddenProductResponse> response = productService.getHiddenProducts(0, 10);

    assertThat(response.content()).hasSize(1);
    AdminHiddenProductResponse item = response.content().getFirst();
    assertThat(item.productId()).isEqualTo(hiddenProduct.getId());
    assertThat(item.title()).isEqualTo("숨김 상품");
    assertThat(item.status()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(item.hidden()).isTrue();
    assertThat(item.sellerNickname()).isEqualTo("열무판매자");
    assertThat(item.updatedAt()).isNotNull();
    assertThat(response.totalElements()).isEqualTo(1);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  void 숨김_상품_목록의_페이지_조건이_잘못되면_실패한다() {
    assertThatThrownBy(() -> productService.getHiddenProducts(0, 0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAGINATION));
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
  void 상품_목록_비회원_조회는_찜_필드를_기본값으로_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    PageResponse<ProductListItemResponse> response =
        productService.getProducts(0, 10, ProductStatus.ON_SALE, "latest", null);

    assertThat(response.content()).hasSize(1);
    ProductListItemResponse item = response.content().getFirst();
    assertThat(item.productId()).isEqualTo(product.getId());
    assertThat(item.wishCount()).isZero();
    assertThat(item.wished()).isFalse();
  }

  @Test
  void 상품_목록은_대표_이미지를_thumbnailUrl로_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    saveProductImage(
        product, "/uploads/products/%d/thumbnail.png".formatted(product.getId()), true);

    PageResponse<ProductListItemResponse> response =
        productService.getProducts(0, 10, ProductStatus.ON_SALE, "latest", null);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().thumbnailUrl())
        .isEqualTo("/uploads/products/%d/thumbnail.png".formatted(product.getId()));
  }

  @Test
  void 상품_목록_로그인_조회는_상품별_찜_수와_사용자_찜_여부를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    User other = saveUser("other@example.com", "다른사용자");
    Product firstProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    Product secondProduct = saveProduct(seller, "맥북 에어", 900000);
    saveWish(viewer, secondProduct);
    saveWish(other, secondProduct);
    saveWish(other, firstProduct);

    PageResponse<ProductListItemResponse> response =
        productService.getProducts(0, 10, ProductStatus.ON_SALE, "latest", viewer.getId());

    assertThat(response.content()).hasSize(2);
    ProductListItemResponse firstItem = response.content().getFirst();
    ProductListItemResponse secondItem = response.content().get(1);
    assertThat(firstItem.productId()).isEqualTo(secondProduct.getId());
    assertThat(firstItem.wishCount()).isEqualTo(2);
    assertThat(firstItem.wished()).isTrue();
    assertThat(secondItem.productId()).isEqualTo(firstProduct.getId());
    assertThat(secondItem.wishCount()).isEqualTo(1);
    assertThat(secondItem.wished()).isFalse();
  }

  @Test
  void 상품_상세_비회원_조회는_찜_필드를_기본값으로_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    ProductDetailResponse response = productService.getProduct(product.getId(), null);

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.wishCount()).isZero();
    assertThat(response.wished()).isFalse();
  }

  @Test
  void 상품_상세는_업로드된_이미지_목록을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    ProductImage firstImage =
        saveProductImage(product, "/uploads/products/%d/1.png".formatted(product.getId()), true);
    ProductImage secondImage =
        saveProductImage(product, "/uploads/products/%d/2.png".formatted(product.getId()), false);

    ProductDetailResponse response = productService.getProduct(product.getId(), null);

    assertThat(response.images()).hasSize(2);
    assertThat(response.images().getFirst().imageId()).isEqualTo(firstImage.getId());
    assertThat(response.images().getFirst().thumbnail()).isTrue();
    assertThat(response.images().get(1).imageId()).isEqualTo(secondImage.getId());
    assertThat(response.images().get(1).thumbnail()).isFalse();
  }

  @Test
  void 상품_상세_로그인_조회는_사용자_찜_여부를_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User viewer = saveUser("viewer@example.com", "조회자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    saveWish(viewer, product);
    saveWish(other, product);

    ProductDetailResponse response = productService.getProduct(product.getId(), viewer.getId());

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.wishCount()).isEqualTo(2);
    assertThat(response.wished()).isTrue();
  }

  @Test
  void 상품을_등록하면_카테고리가_저장된다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category category = saveCategory("디지털기기");

    CreateProductResponse response =
        productService.createProduct(
            seller.getId(),
            new CreateProductRequest("아이패드 미니 6", "생활기스 조금 있습니다.", 450000, category.getId()));

    assertThat(response.productId()).isNotNull();
    Product savedProduct = productRepository.findById(response.productId()).orElseThrow();
    assertThat(savedProduct.getCategory().getId()).isEqualTo(category.getId());
  }

  @Test
  void 존재하지_않는_카테고리로_상품을_등록하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");

    assertThatThrownBy(
            () ->
                productService.createProduct(
                    seller.getId(),
                    new CreateProductRequest("아이패드 미니 6", "생활기스 조금 있습니다.", 450000, Long.MAX_VALUE)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
  }

  @Test
  void 상품은_카테고리_없이_생성할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");

    assertThatThrownBy(() -> Product.create(seller, "아이패드 미니 6", "생활기스 조금 있습니다.", 450000, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("category는 필수입니다.");
  }

  @Test
  void 판매자가_자신의_상품을_수정하면_변경값을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    UpdateProductResponse response =
        productService.updateProduct(
            seller.getId(),
            product.getId(),
            new UpdateProductRequest("아이패드 미니 6세대", null, 430000, null));

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.title()).isEqualTo("아이패드 미니 6세대");
    assertThat(response.description()).isEqualTo("생활기스 조금 있습니다.");
    assertThat(response.price()).isEqualTo(430000);
    assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.updatedAt()).isNotNull();

    Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(updatedProduct.getTitle()).isEqualTo("아이패드 미니 6세대");
    assertThat(updatedProduct.getPrice()).isEqualTo(430000);
  }

  @Test
  void 판매자가_자신의_상품_카테고리를_수정할_수_있다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Category newCategory = saveCategory("가구");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    UpdateProductResponse response =
        productService.updateProduct(
            seller.getId(),
            product.getId(),
            new UpdateProductRequest(null, null, null, newCategory.getId()));

    assertThat(response.productId()).isEqualTo(product.getId());
    Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(updatedProduct.getCategory().getId()).isEqualTo(newCategory.getId());
    assertThat(updatedProduct.getTitle()).isEqualTo("아이패드 미니 6");
    assertThat(updatedProduct.getPrice()).isEqualTo(450000);
  }

  @Test
  void 존재하지_않는_카테고리로_상품을_수정하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    seller.getId(),
                    product.getId(),
                    new UpdateProductRequest(null, null, null, Long.MAX_VALUE)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
  }

  @Test
  void 판매자가_아닌_사용자가_상품을_수정하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    other.getId(),
                    product.getId(),
                    new UpdateProductRequest("아이패드 미니 6세대", null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
  }

  @Test
  void 수정할_값이_없으면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    seller.getId(),
                    product.getId(),
                    new UpdateProductRequest(null, null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void 판매자가_자신의_상품을_삭제하면_삭제_상태로_변경한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    DeleteProductResponse response = productService.deleteProduct(seller.getId(), product.getId());

    assertThat(response.deleted()).isTrue();
    Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(deletedProduct.getStatus()).isEqualTo(ProductStatus.DELETED);
    assertThat(deletedProduct.getDeletedAt()).isNotNull();
  }

  @Test
  void 거래_진행_중인_상품은_삭제할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithStatus(seller, "아이패드 미니 6", 450000, ProductStatus.RESERVED);

    assertThatThrownBy(() -> productService.deleteProduct(seller.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_HAS_ACTIVE_ORDER));
  }

  @Test
  void 상품을_숨김_처리하면_hidden만_변경하고_상품_상태는_유지한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithStatus(seller, "예약 상품", 20000, ProductStatus.RESERVED);

    UpdateProductHiddenStatusResponse response =
        productService.updateProductHiddenStatus(
            product.getId(), new UpdateProductHiddenStatusRequest(true));

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.status()).isEqualTo(ProductStatus.RESERVED);
    assertThat(response.hidden()).isTrue();

    Product hiddenProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(hiddenProduct.getStatus()).isEqualTo(ProductStatus.RESERVED);
    assertThat(hiddenProduct.isHidden()).isTrue();
  }

  @Test
  void 숨김_상품을_숨김_해제할_수_있다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);
    product.changeHidden(true);
    productRepository.flush();

    UpdateProductHiddenStatusResponse response =
        productService.updateProductHiddenStatus(
            product.getId(), new UpdateProductHiddenStatusRequest(false));

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.hidden()).isFalse();

    Product visibleProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(visibleProduct.isHidden()).isFalse();
  }

  @Test
  void 존재하지_않는_상품_숨김_상태_변경은_실패한다() {
    assertThatThrownBy(
            () ->
                productService.updateProductHiddenStatus(
                    Long.MAX_VALUE, new UpdateProductHiddenStatusRequest(true)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 삭제된_상품_숨김_상태_변경은_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProductWithStatus(seller, "삭제 상품", 20000, ProductStatus.DELETED);

    assertThatThrownBy(
            () ->
                productService.updateProductHiddenStatus(
                    product.getId(), new UpdateProductHiddenStatusRequest(true)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  private void deleteAll() {
    wishRepository.deleteAll();
    productImageRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
  }

  private void saveWish(User user, Product product) {
    wishRepository.saveAndFlush(Wish.create(user, product));
  }

  private ProductImage saveProductImage(Product product, String url, boolean thumbnail) {
    return productImageRepository.saveAndFlush(ProductImage.create(product, url, thumbnail));
  }

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveDeletedHiddenProduct(User seller, String title, Integer price) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    product.delete(DELETED_AT);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product =
        ProductTestFactory.createProduct(categoryRepository, seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private Category saveCategory(String name) {
    return categoryRepository.saveAndFlush(Category.create(name));
  }
}
