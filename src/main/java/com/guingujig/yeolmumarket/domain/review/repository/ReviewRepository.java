package com.guingujig.yeolmumarket.domain.review.repository;

import com.guingujig.yeolmumarket.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  boolean existsByOrderIdAndReviewerId(Long orderId, Long reviewerId);
}
