package com.guingujig.yeolmumarket.domain.review.controller;

import com.guingujig.yeolmumarket.domain.review.dto.CreateReviewRequest;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewResponse;
import com.guingujig.yeolmumarket.domain.review.service.ReviewService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @PostMapping("/api/orders/{orderId}/reviews")
  public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long orderId,
      @Valid @RequestBody CreateReviewRequest request) {
    ReviewResponse response =
        reviewService.createReview(
            authenticatedUser.userId(), orderId, request.score(), request.content());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}
