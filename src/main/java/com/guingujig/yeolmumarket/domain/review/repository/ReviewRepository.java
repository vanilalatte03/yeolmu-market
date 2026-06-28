package com.guingujig.yeolmumarket.domain.review.repository;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  boolean existsByOrderIdAndReviewerId(Long orderId, Long reviewerId);

  @EntityGraph(attributePaths = {"order", "reviewer", "reviewee"})
  Optional<Review> findByIdAndOrderId(Long id, Long orderId);
}
