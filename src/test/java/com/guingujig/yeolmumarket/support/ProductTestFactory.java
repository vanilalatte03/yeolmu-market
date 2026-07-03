package com.guingujig.yeolmumarket.support;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.category.repository.CategoryRepository;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트에서 유효한 {@link Product} fixture를 만들기 위한 헬퍼다.
 *
 * <p>P1부터 신규 상품은 카테고리 없이 저장할 수 없다.
 *
 * <p>이 헬퍼는 고유한 테스트 카테고리를 저장하고 상품 생성에 연결한다. 상품 fixture가 필요한 테스트의 반복 설정을 줄이는 용도다.
 *
 * <p>카테고리 자체가 검증 대상인 테스트에서는 이 헬퍼보다 카테고리를 명시적으로 생성해 테스트 의도를 드러내는 것이 낫다.
 */
public final class ProductTestFactory {

  private static final AtomicLong CATEGORY_SEQUENCE = new AtomicLong();

  private ProductTestFactory() {}

  public static Product saveProduct(
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      User seller,
      String title,
      String description,
      Integer price) {
    Product product = createProduct(categoryRepository, seller, title, description, price);
    return productRepository.saveAndFlush(product);
  }

  public static Product createProduct(
      CategoryRepository categoryRepository,
      User seller,
      String title,
      String description,
      Integer price) {
    Category category = saveCategory(categoryRepository);
    return Product.create(seller, title, description, price, category);
  }

  private static Category saveCategory(CategoryRepository categoryRepository) {
    String name = "cat-" + CATEGORY_SEQUENCE.incrementAndGet();
    return categoryRepository.saveAndFlush(Category.create(name));
  }
}
