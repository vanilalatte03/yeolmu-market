package com.guingujig.yeolmumarket.domain.wish.repository;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishRepository extends JpaRepository<Wish, Long> {

  boolean existsByUserIdAndProductId(Long userId, Long productId);

  Optional<Wish> findByUserAndProduct(User user, Product product);

  long countByProductId(Long productId);

  @EntityGraph(attributePaths = "product")
  @Query(
      value =
          """
          select wish
          from Wish wish
          join wish.product product
          where wish.user.id = :userId
            and product.hidden = false
            and product.deletedAt is null
            and product.status <> :deletedStatus
          """,
      countQuery =
          """
          select count(wish)
          from Wish wish
          join wish.product product
          where wish.user.id = :userId
            and product.hidden = false
            and product.deletedAt is null
            and product.status <> :deletedStatus
          """)
  Page<Wish> findPublicWishesByUserId(
      @Param("userId") Long userId,
      @Param("deletedStatus") ProductStatus deletedStatus,
      Pageable pageable);

  @Query(
      """
      select wish.product.id as productId, count(wish.id) as wishCount
      from Wish wish
      where wish.product.id in :productIds
      group by wish.product.id
      """)
  List<ProductWishCount> countByProductIds(@Param("productIds") Collection<Long> productIds);

  @Query(
      """
      select wish.product.id
      from Wish wish
      where wish.user.id = :userId
        and wish.product.id in :productIds
      """)
  Set<Long> findWishedProductIdsByUserId(
      @Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

  interface ProductWishCount {
    Long getProductId();

    long getWishCount();
  }
}
