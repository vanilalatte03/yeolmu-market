package com.guingujig.yeolmumarket.domain.wish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class WishServiceTest {

  private final WishService wishService;
  private final WishRepository wishRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  WishServiceTest(
      WishService wishService,
      WishRepository wishRepository,
      ProductRepository productRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.wishService = wishService;
    this.wishRepository = wishRepository;
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

  private void deleteAll() {
    wishRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();
  }

  private Product saveProduct(User seller) {
    return productRepository.save(Product.create(seller, "아이패드 미니 6", "생활기스", 450000));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }
}
