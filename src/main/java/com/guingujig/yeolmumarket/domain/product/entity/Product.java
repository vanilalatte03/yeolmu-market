package com.guingujig.yeolmumarket.domain.product.entity;

import com.guingujig.yeolmumarket.domain.category.entity.Category;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.global.entity.BaseTimeEntity;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
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
  @JoinColumn(name = "category_id", nullable = false)
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
   * <p>P1부터 신규 상품은 카테고리 없이 저장할 수 없다.
   */
  public static Product create(
      User seller, String title, String description, Integer price, Category category) {
    Product product = new Product();
    product.seller = Objects.requireNonNull(seller, "seller는 필수입니다.");
    product.category = Objects.requireNonNull(category, "category는 필수입니다.");
    product.title = requireText(title, "상품명은 필수입니다.");
    product.description = requireText(description, "상품 설명은 필수입니다.");
    product.price = requirePositive(price);
    product.status = ProductStatus.ON_SALE;
    product.hidden = false;
    return product;
  }

  /** 상품 기본 정보인 제목, 설명, 가격을 부분 변경한다. 카테고리 변경은 {@link #changeCategory(Category)}에서 처리한다. */
  public void updateInfo(String title, String description, Integer price) {
    if (title != null) {
      this.title = requireText(title, "상품명은 필수입니다.");
    }
    if (description != null) {
      this.description = requireText(description, "상품 설명은 필수입니다.");
    }
    if (price != null) {
      this.price = requirePositive(price);
    }
  }

  /** 상품 카테고리를 변경한다. P1 이후 상품은 항상 유효한 카테고리에 속해야 한다. */
  public void changeCategory(Category category) {
    this.category = Objects.requireNonNull(category, "category는 필수입니다.");
  }

  /** 판매자 삭제는 행을 제거하지 않고 공개 조회에서 제외되는 삭제 상태와 삭제 시각만 기록한다. */
  public void delete(LocalDateTime deletedAt) {
    this.status = ProductStatus.DELETED;
    this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt은 필수입니다.");
  }

  /** 관리자 숨김 처리는 상품 거래 상태를 유지하고 공개 노출 여부만 변경한다. */
  public void changeHidden(boolean hidden) {
    this.hidden = hidden;
  }

  public boolean isDeleted() {
    return status == ProductStatus.DELETED || deletedAt != null;
  }

  public boolean hasActiveOrder() {
    return status == ProductStatus.RESERVED;
  }

  /**
   * ON_SALE 상태의 상품을 RESERVED로 전이한다.
   *
   * <p>ON_SALE이 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void reserve() {
    if (this.status != ProductStatus.ON_SALE) {
      throw new BusinessException(ErrorCode.PRODUCT_INVALID_STATUS);
    }
    this.status = ProductStatus.RESERVED;
  }

  /**
   * RESERVED 상태의 상품을 ON_SALE로 전이한다. 주문 취소 시 예약을 해제하는 데 사용한다.
   *
   * <p>RESERVED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void cancelReservation() {
    if (this.status != ProductStatus.RESERVED) {
      throw new BusinessException(ErrorCode.PRODUCT_INVALID_STATUS);
    }
    this.status = ProductStatus.ON_SALE;
  }

  /**
   * RESERVED 상태의 상품을 SOLD_OUT으로 전이한다. 구매 확정 시 최종 판매 완료 처리에 사용한다.
   *
   * <p>RESERVED가 아닌 상태에서 호출하면 {@link BusinessException}을 던져 잘못된 전이를 차단한다.
   */
  public void completeSale() {
    if (this.status != ProductStatus.RESERVED) {
      throw new BusinessException(ErrorCode.PRODUCT_INVALID_STATUS);
    }
    this.status = ProductStatus.SOLD_OUT;
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
