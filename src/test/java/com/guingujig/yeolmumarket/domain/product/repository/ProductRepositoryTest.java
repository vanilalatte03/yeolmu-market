package com.guingujig.yeolmumarket.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class ProductRepositoryTest {

  private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 6, 24, 10, 0);

  private final ProductRepository productRepository;
  private final UserRepository userRepository;

  @Autowired
  ProductRepositoryTest(ProductRepository productRepository, UserRepository userRepository) {
    this.productRepository = productRepository;
    this.userRepository = userRepository;
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
  void 숨김_상품_조회는_hidden_true이고_삭제되지_않은_상품만_반환한다() {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product hiddenProduct = saveHiddenProduct(seller, "숨김 상품", 20000);
    saveProduct(seller, "공개 상품", 30000);
    saveDeletedAtHiddenProduct(seller, "삭제일 있는 숨김 상품", 40000);
    saveDeletedStatusHiddenProduct(seller, "삭제 상태 숨김 상품", 50000);

    Page<Product> products =
        productRepository.findByHiddenTrueAndDeletedAtIsNullAndStatusNot(
            ProductStatus.DELETED,
            PageRequest.of(0, 10, Sort.by(Sort.Order.desc("modifiedAt"), Sort.Order.desc("id"))));

    assertThat(products.getContent())
        .extracting(Product::getId)
        .containsExactly(hiddenProduct.getId());
    assertThat(products.getTotalElements()).isEqualTo(1);
    assertThat(products.getContent().getFirst().getSeller().getNickname()).isEqualTo("열무판매자");
  }

  private void deleteAll() {
    productRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, "encoded-password", nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    return productRepository.saveAndFlush(product);
  }

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveDeletedAtHiddenProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    ReflectionTestUtils.setField(product, "deletedAt", DELETED_AT);
    return productRepository.saveAndFlush(product);
  }

  private Product saveDeletedStatusHiddenProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    ReflectionTestUtils.setField(product, "status", ProductStatus.DELETED);
    return productRepository.saveAndFlush(product);
  }
}
