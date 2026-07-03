package com.guingujig.yeolmumarket.domain.review.service;

import com.guingujig.yeolmumarket.domain.review.dto.ReviewRatingSummary;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewRatingQueryService {

  private final ReviewRepository reviewRepository;

  /** 특정 유저가 받은 현재 리뷰 기준 평점 요약을 조회한다. */
  @Transactional(readOnly = true)
  public ReviewRatingSummary getSummary(Long revieweeId) {
    return reviewRepository.findRatingSummaryByRevieweeId(revieweeId);
  }
}
