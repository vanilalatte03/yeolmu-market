package com.guingujig.yeolmumarket.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductImageRepository;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.config.YeolmuProperties;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProductImageRepository productImageRepository;
  @Mock private ProductChangeEventPublisher productChangeEventPublisher;

  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService =
        new ProductService(
            productRepository,
            productImageRepository,
            productChangeEventPublisher,
            new YeolmuProperties(null, null));
  }

  @Test
  void 주문용_상품_예약은_공개_상품을_RESERVED로_전이하고_변경_이벤트를_발행한다() {
    Product product = product(10L, seller(1L), ProductStatus.ON_SALE, false, null);
    when(productRepository.findWithSellerById(10L)).thenReturn(Optional.of(product));

    Product reserved = productService.reservePublicProductForOrder(2L, 10L);

    assertThat(reserved).isSameAs(product);
    assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
    verify(productRepository).flush();
    verify(productChangeEventPublisher)
        .publishSearchIndexAndDisplayChanged(10L, ProductStatus.ON_SALE, ProductStatus.RESERVED);
  }

  @Test
  void 주문용_상품_예약은_판매자_본인_주문을_거부한다() {
    Product product = product(10L, seller(1L), ProductStatus.ON_SALE, false, null);
    when(productRepository.findWithSellerById(10L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.reservePublicProductForOrder(1L, 10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANNOT_ORDER_OWN_PRODUCT));
  }

  @Test
  void 주문용_상품_예약은_낙관락_flush_실패를_주문_중복으로_변환한다() {
    Product product = product(10L, seller(1L), ProductStatus.ON_SALE, false, null);
    when(productRepository.findWithSellerById(10L)).thenReturn(Optional.of(product));
    doThrow(new ObjectOptimisticLockingFailureException(Product.class, 10L))
        .when(productRepository)
        .flush();

    assertThatThrownBy(() -> productService.reservePublicProductForOrder(2L, 10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_EXISTS));
  }

  @Test
  void 채팅방용_상품_조회는_pessimistic_lock으로_판매자_포함_상품을_조회하고_생성_가능성을_검증한다() {
    Product product = product(10L, seller(1L), ProductStatus.ON_SALE, false, null);
    when(productRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(product));

    Product chatCreatableProduct = productService.getChatCreatableProductForUpdate(10L, 2L);

    assertThat(chatCreatableProduct).isSameAs(product);
    assertThat(chatCreatableProduct.getSeller().getId()).isEqualTo(1L);
  }

  @Test
  void 채팅방용_상품_조회는_판매자_본인_생성을_거부한다() {
    Product product = product(10L, seller(1L), ProductStatus.ON_SALE, false, null);
    when(productRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.getChatCreatableProductForUpdate(10L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANNOT_CHAT_OWN_PRODUCT));
  }

  @Test
  void 채팅방용_상품_조회는_없는_상품이면_실패한다() {
    when(productRepository.findWithSellerByIdForUpdate(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getChatCreatableProductForUpdate(10L, 2L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 카테고리_상품_조회는_썸네일_없이_공개_상품_Page만_조회한다() {
    Page<Product> emptyPage = Page.empty();
    when(productRepository.findByCategoryIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
            eq(20L), eq(ProductStatus.DELETED), any(PageRequest.class)))
        .thenReturn(emptyPage);

    Page<Product> products =
        productService.getPublicCategoryProducts(new CategoryProductsQuery(20L, 0, 10, "priceAsc"));

    assertThat(products).isSameAs(emptyPage);
    ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(productRepository)
        .findByCategoryIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
            eq(20L), eq(ProductStatus.DELETED), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("price").isAscending()).isTrue();
  }

  private Product product(
      Long productId, User seller, ProductStatus status, boolean hidden, LocalDateTime deletedAt) {
    Product product = Product.create(seller, "아이패드 미니 6", "생활기스", 450000, Category.create("디지털기기"));
    ReflectionTestUtils.setField(product, "id", productId);
    ReflectionTestUtils.setField(product, "status", status);
    ReflectionTestUtils.setField(product, "hidden", hidden);
    ReflectionTestUtils.setField(product, "deletedAt", deletedAt);
    return product;
  }

  private User seller(Long sellerId) {
    User seller = new User("seller@example.com", "encoded-password", "열무판매자");
    ReflectionTestUtils.setField(seller, "id", sellerId);
    return seller;
  }
}
