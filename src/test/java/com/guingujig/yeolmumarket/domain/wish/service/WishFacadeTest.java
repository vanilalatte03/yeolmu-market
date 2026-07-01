package com.guingujig.yeolmumarket.domain.wish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
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
class WishFacadeTest {

  private final WishFacade wishFacade;
  private final WishRepository wishRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  WishFacadeTest(
      WishFacade wishFacade,
      WishRepository wishRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.wishFacade = wishFacade;
    this.wishRepository = wishRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
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
  void 찜을_생성하면_유저와_공개_상품을_조회해_저장한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);

    WishResponse response = wishFacade.createWish(user.getId(), product.getId());

    assertThat(response.productId()).isEqualTo(product.getId());
    assertThat(response.wished()).isTrue();
    assertThat(response.wishCount()).isEqualTo(1);
    assertThat(wishRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue();
  }

  @Test
  void 존재하지_않는_유저는_찜할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product product = saveProduct(seller);

    assertThatThrownBy(() -> wishFacade.createWish(Long.MAX_VALUE, product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  @Test
  void 숨김_상품은_찜할_수_없다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    User user = saveUser("user@example.com", "열무유저");
    Product product = saveProduct(seller);
    ReflectionTestUtils.setField(product, "hidden", true);
    productRepository.saveAndFlush(product);

    assertThatThrownBy(() -> wishFacade.createWish(user.getId(), product.getId()))
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

    assertThatThrownBy(() -> wishFacade.createWish(user.getId(), product.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
  }

  @Test
  void 존재하지_않는_유저의_찜_목록은_USER_NOT_FOUND를_반환한다() {
    assertThatThrownBy(() -> wishFacade.getMyWishes(Long.MAX_VALUE, 0, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  private void deleteAll() {
    wishRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
  }

  private Product saveProduct(User seller) {
    return ProductTestFactory.saveProduct(
        productRepository, categoryRepository, seller, "아이패드 미니 6", "생활기스", 450000);
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
