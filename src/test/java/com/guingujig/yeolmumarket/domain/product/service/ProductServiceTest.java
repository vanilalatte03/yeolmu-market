package com.guingujig.yeolmumarket.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.product.dto.DeleteProductResponse;
import com.guingujig.yeolmumarket.domain.product.dto.HiddenProductListItemResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductHiddenStatusResponse;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductRequest;
import com.guingujig.yeolmumarket.domain.product.dto.UpdateProductResponse;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.PageResponse;
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
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  ProductServiceTest(
      ProductService productService,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.productService = productService;
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
  void 판매자가_자신의_상품을_수정하면_변경값을_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    UpdateProductResponse response =
        productService.updateProduct(
            seller.getId(), product.getId(), new UpdateProductRequest("아이패드 미니 6세대", null, 430000));

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
  void 판매자가_아닌_사용자가_상품을_수정하면_실패한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User other = saveUser("other@example.com", "다른사용자");
    Product product = saveProduct(seller, "아이패드 미니 6", 450000);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    other.getId(),
                    product.getId(),
                    new UpdateProductRequest("아이패드 미니 6세대", null, null)))
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
                    seller.getId(), product.getId(), new UpdateProductRequest(null, null, null)))
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

  @Test
  void 숨김_상품_목록은_숨김_상품만_반환하고_삭제_상품은_제외한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product hiddenProduct = saveHiddenProduct(seller, "숨김 상품", 20000);
    saveProduct(seller, "공개 상품", 10000);
    saveHiddenProductWithStatus(seller, "삭제 숨김 상품", 30000, ProductStatus.DELETED);

    PageResponse<HiddenProductListItemResponse> response = productService.getHiddenProducts(0, 10);

    assertThat(response.totalElements()).isEqualTo(1);
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().productId()).isEqualTo(hiddenProduct.getId());
    assertThat(response.content().getFirst().hidden()).isTrue();
  }

  private void deleteAll() {
    productRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveHiddenProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }
}
