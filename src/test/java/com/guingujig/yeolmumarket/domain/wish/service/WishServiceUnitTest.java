package com.guingujig.yeolmumarket.domain.wish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import com.guingujig.yeolmumarket.domain.wish.repository.WishRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WishServiceUnitTest {

  @Mock private WishRepository wishRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;

  @Test
  void 저장_중_unique_제약_위반이_발생하면_중복_찜_에러로_변환한다() {
    WishService wishService = new WishService(wishRepository, productRepository, userRepository);
    User user = new User("user@example.com", "encodedPassword", "열무유저");
    User seller = new User("seller@example.com", "encodedPassword", "열무판매자");
    Product product = Product.create(seller, "아이패드 미니 6", "생활기스", 450000, Category.create("디지털기기"));
    Long userId = 1L;
    Long productId = 10L;
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(productRepository.findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
            productId, ProductStatus.DELETED))
        .thenReturn(Optional.of(product));
    when(wishRepository.existsByUserIdAndProductId(userId, productId)).thenReturn(false);
    when(wishRepository.saveAndFlush(any(Wish.class)))
        .thenThrow(new DataIntegrityViolationException("uk_wish_user_product"));

    assertThatThrownBy(() -> wishService.createWish(userId, productId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WISH_ALREADY_EXISTS));
  }
}
