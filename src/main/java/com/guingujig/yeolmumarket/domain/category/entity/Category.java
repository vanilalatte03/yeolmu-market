package com.guingujig.yeolmumarket.domain.category.entity;

import com.guingujig.yeolmumarket.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "category",
    uniqueConstraints = @UniqueConstraint(name = "uk_category_name", columnNames = "name"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String name;

  public static Category create(String name) {
    Category category = new Category();
    category.name = requireName(name);
    return category;
  }

  public void updateName(String name) {
    this.name = requireName(name);
  }

  private static String requireName(String name) {
    String requiredName = Objects.requireNonNull(name, "카테고리명은 필수입니다.");
    if (requiredName.isBlank()) {
      throw new IllegalArgumentException("카테고리명은 공백일 수 없습니다.");
    }
    return requiredName;
  }
}
