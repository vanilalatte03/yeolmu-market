package com.guingujig.yeolmumarket.domain.product.repository;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  @EntityGraph(attributePaths = "seller")
  Optional<Product> findWithSellerById(Long id);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findByHiddenFalseAndDeletedAtIsNullAndStatus(
      ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Optional<Product> findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
      Long id, ProductStatus status);

  @EntityGraph(attributePaths = "seller")
  @Query(
      """
      SELECT p
      FROM Product p
      WHERE p.hidden = false
        AND p.deletedAt IS NULL
        AND p.status = :status
        AND (
          :keyword IS NULL
          OR LOWER(p.title) LIKE CONCAT('%', :keyword, '%')
          OR LOWER(p.description) LIKE CONCAT('%', :keyword, '%')
        )
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
      """)
  Page<Product> searchPublicProducts(
      @Param("keyword") String keyword,
      @Param("minPrice") Integer minPrice,
      @Param("maxPrice") Integer maxPrice,
      @Param("status") ProductStatus status,
      Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findByHiddenTrueAndDeletedAtIsNullAndStatusNot(
      ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findBySellerIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
      Long sellerId, ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findBySellerIdAndHiddenFalseAndDeletedAtIsNullAndStatus(
      Long sellerId, ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findBySellerIdAndDeletedAtIsNullAndStatusNot(
      Long sellerId, ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findBySellerIdAndDeletedAtIsNullAndStatus(
      Long sellerId, ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findBySellerIdAndStatus(Long sellerId, ProductStatus status, Pageable pageable);
}
