package com.guingujig.yeolmumarket.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "product_image")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String url;

  // 상품당 대표 이미지는 1개만 허용한다. 단, 단순 UNIQUE(product_id, is_thumbnail)는
  // 일반 이미지(false)까지 1개로 제한하므로 서비스 또는 DB 제약 설계에서 별도로 보장한다.
  @Column(name = "is_thumbnail", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
  private boolean thumbnail = false;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static ProductImage create(Product product, String url, boolean thumbnail) {
    ProductImage productImage = new ProductImage();
    productImage.product = Objects.requireNonNull(product, "product는 필수입니다.");
    productImage.url = requireText(url);
    productImage.thumbnail = thumbnail;
    return productImage;
  }

  public void markAsThumbnail() {
    this.thumbnail = true;
  }

  public void unmarkAsThumbnail() {
    this.thumbnail = false;
  }

  private static String requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("이미지 URL은 필수입니다.");
    }
    return value;
  }
}
