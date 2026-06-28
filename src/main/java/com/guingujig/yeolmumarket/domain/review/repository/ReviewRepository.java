package com.guingujig.yeolmumarket.domain.review.repository;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  boolean existsByOrderIdAndReviewerId(Long orderId, Long reviewerId);

  @EntityGraph(attributePaths = {"order", "reviewee"})
  Page<Review> findByReviewerId(Long reviewerId, Pageable pageable);

  @EntityGraph(attributePaths = {"order", "reviewer"})
  Page<Review> findByRevieweeId(Long revieweeId, Pageable pageable);
}
