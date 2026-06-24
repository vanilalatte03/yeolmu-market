package com.guingujig.yeolmumarket.domain.product.entity;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private User seller;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private Integer price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status;

  @Column(nullable = false)
  private boolean hidden;

  @Version
  @Column(nullable = false)
  private Integer version;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /**
   * 신규 상품은 등록 즉시 판매 중이며 일반 사용자에게 노출된다.
   *
   * <p>카테고리와 이미지는 P1 범위이므로 P0 상품 등록에서는 비워 둔다.
   */
  public static Product create(User seller, String title, String description, Integer price) {
    Product product = new Product();
    product.seller = Objects.requireNonNull(seller, "seller는 필수입니다.");
    product.title = requireText(title, "상품명은 필수입니다.");
    product.description = requireText(description, "상품 설명은 필수입니다.");
    product.price = requirePositive(price);
    product.status = ProductStatus.ON_SALE;
    product.hidden = false;
    return product;
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static Integer requirePositive(Integer price) {
    if (price == null || price <= 0) {
      throw new IllegalArgumentException("상품 가격은 0보다 커야 합니다.");
    }
    return price;
  }
}
