package com.guingujig.yeolmumarket.domain.product.repository;

import com.guingujig.yeolmumarket.domain.product.dto.ProductListItemProjection;
import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  @EntityGraph(attributePaths = "seller")
  Optional<Product> findWithSellerById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "seller")
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findWithSellerByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          """
          select new com.guingujig.yeolmumarket.domain.product.dto.ProductListItemProjection(
            product.id,
            product.title,
            product.price,
            product.status,
            thumbnail.url,
            seller.nickname,
            product.createdAt
          )
          from Product product
          join product.seller seller
          left join ProductImage thumbnail
            on thumbnail.product = product
           and thumbnail.thumbnail = true
          where product.hidden = false
            and product.deletedAt is null
            and product.status = :status
          """,
      countQuery =
          """
          select count(product)
          from Product product
          where product.hidden = false
            and product.deletedAt is null
            and product.status = :status
          """)
  Page<ProductListItemProjection> findPublicListItemsByStatus(
      @Param("status") ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Page<Product> findByCategoryIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
      Long categoryId, ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  Optional<Product> findByIdAndHiddenFalseAndDeletedAtIsNullAndStatusNot(
      Long id, ProductStatus status);

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

  @EntityGraph(attributePaths = "seller")
  Page<Product> findByHiddenTrueAndDeletedAtIsNullAndStatusNot(
      ProductStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "seller")
  @Query(
      """
      select product
      from Product product
      where product.hidden = false
        and product.deletedAt is null
        and product.status = :status
        and (
          :keyword is null
          or lower(product.title) like lower(concat(concat('%', :keyword), '%')) escape '!'
          or lower(product.description) like lower(concat(concat('%', :keyword), '%')) escape '!'
        )
        and (:minPrice is null or product.price >= :minPrice)
        and (:maxPrice is null or product.price <= :maxPrice)
      """)
  Page<Product> searchPublicProducts(
      @Param("keyword") String keyword,
      @Param("minPrice") Integer minPrice,
      @Param("maxPrice") Integer maxPrice,
      @Param("status") ProductStatus status,
      Pageable pageable);
}
