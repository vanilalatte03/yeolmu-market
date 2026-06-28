package com.guingujig.yeolmumarket.domain.review.controller;

import com.guingujig.yeolmumarket.domain.review.dto.CreateReviewRequest;
import com.guingujig.yeolmumarket.domain.review.dto.DeleteReviewResponse;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewResponse;
import com.guingujig.yeolmumarket.domain.review.dto.UpdateReviewRequest;
import com.guingujig.yeolmumarket.domain.review.dto.UpdateReviewResponse;
import com.guingujig.yeolmumarket.domain.review.service.ReviewService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

  @PatchMapping("/api/orders/{orderId}/reviews/{reviewId}")
  public ResponseEntity<ApiResponse<UpdateReviewResponse>> updateReview(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long orderId,
      @PathVariable Long reviewId,
      @Valid @RequestBody UpdateReviewRequest request) {
    UpdateReviewResponse response =
        reviewService.updateReview(
            authenticatedUser.userId(), orderId, reviewId, request.score(), request.content());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/api/orders/{orderId}/reviews/{reviewId}")
  public ResponseEntity<ApiResponse<DeleteReviewResponse>> deleteReview(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long orderId,
      @PathVariable Long reviewId) {
    DeleteReviewResponse response =
        reviewService.deleteReview(authenticatedUser.userId(), orderId, reviewId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
