package com.guingujig.yeolmumarket.domain.wish.repository;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.wish.entity.Wish;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishRepository extends JpaRepository<Wish, Long> {

  boolean existsByUserIdAndProductId(Long userId, Long productId);

  Optional<Wish> findByUserAndProduct(User user, Product product);

  long countByProductId(Long productId);
}
