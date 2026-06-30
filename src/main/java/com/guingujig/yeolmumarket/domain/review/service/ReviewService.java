package com.guingujig.yeolmumarket.domain.review.service;

import com.guingujig.yeolmumarket.domain.review.dto.DeleteReviewResponse;
import com.guingujig.yeolmumarket.domain.review.dto.PublicReceivedReviewListItemResponse;
import com.guingujig.yeolmumarket.domain.review.dto.ReceivedReviewListItemResponse;
import com.guingujig.yeolmumarket.domain.review.dto.ReviewResponse;
import com.guingujig.yeolmumarket.domain.review.dto.UpdateReviewResponse;
import com.guingujig.yeolmumarket.domain.review.dto.WrittenReviewListItemResponse;
import com.guingujig.yeolmumarket.domain.review.entity.Review;
import com.guingujig.yeolmumarket.domain.review.repository.ReviewRepository;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.lock.DistributedLockExecutor;
import com.guingujig.yeolmumarket.global.lock.LockKeys;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final DistributedLockExecutor distributedLockExecutor;
  private final ReviewLockedCommandService reviewLockedCommandService;

  private static final int MAX_PAGE_SIZE = 100;

  /**
   * 거래 완료 주문 참여자가 같은 주문의 상대방에게 리뷰를 작성한다.
   *
   * @throws BusinessException VALIDATION_FAILED - 평점 또는 내용이 유효하지 않은 경우
   * @throws BusinessException ORDER_NOT_FOUND - 주문이 존재하지 않는 경우
   * @throws BusinessException ORDER_ACCESS_DENIED - 주문 참여자가 아닌 사용자의 요청
   * @throws BusinessException REVIEW_NOT_ALLOWED - COMPLETED가 아닌 주문에 리뷰 작성 요청
   * @throws BusinessException REVIEW_ALREADY_EXISTS - 같은 주문에 이미 리뷰를 작성한 경우
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ReviewResponse createReview(Long reviewerId, Long orderId, Integer score, String content) {
    validateScore(score);
    String normalizedContent = normalizeContent(content);

    return distributedLockExecutor.execute(
        LockKeys.order(orderId),
        () ->
            reviewLockedCommandService.createReview(reviewerId, orderId, score, normalizedContent));
  }

  /**
   * 로그인 사용자가 작성했거나 받은 리뷰 목록을 조회한다.
   *
   * @throws BusinessException VALIDATION_FAILED - status가 누락되었거나 허용 값이 아닌 경우
   * @throws BusinessException INVALID_PAGINATION - 페이지 번호 또는 크기가 유효하지 않은 경우
   */
  @Transactional(readOnly = true)
  public PageResponse<?> getMyReviews(Long userId, String status, int page, int size) {
    validatePagination(page, size);
    ReviewListStatus reviewListStatus = ReviewListStatus.from(status);
    PageRequest pageable = PageRequest.of(page, size, reviewSort());

    return switch (reviewListStatus) {
      case WRITTEN ->
          PageResponse.from(
              reviewRepository
                  .findByReviewerId(userId, pageable)
                  .map(WrittenReviewListItemResponse::from));
      case RECEIVED ->
          PageResponse.from(
              reviewRepository
                  .findByRevieweeId(userId, pageable)
                  .map(ReceivedReviewListItemResponse::from));
    };
  }

  /**
   * 특정 유저가 받은 공개 리뷰 목록을 조회한다.
   *
   * @throws BusinessException USER_NOT_FOUND - 대상 유저가 존재하지 않는 경우
   * @throws BusinessException INVALID_PAGINATION - 페이지 번호 또는 크기가 유효하지 않은 경우
   */
  @Transactional(readOnly = true)
  public PageResponse<PublicReceivedReviewListItemResponse> getReceivedReviews(
      Long userId, int page, int size) {
    validatePagination(page, size);
    validateUserExists(userId);

    return PageResponse.from(
        reviewRepository
            .findByRevieweeId(userId, PageRequest.of(page, size, reviewSort()))
            .map(PublicReceivedReviewListItemResponse::from));
  }

  /**
   * 리뷰 작성자 본인만 같은 주문에 속한 리뷰의 평점 또는 내용을 수정할 수 있다.
   *
   * @throws BusinessException VALIDATION_FAILED - 수정할 값이 없거나 값이 유효하지 않은 경우
   * @throws BusinessException REVIEW_NOT_FOUND - 리뷰가 없거나 주문과 리뷰가 일치하지 않는 경우
   * @throws BusinessException REVIEW_ACCESS_DENIED - 리뷰 작성자가 아닌 사용자의 요청
   */
  @Transactional
  public UpdateReviewResponse updateReview(
      Long reviewerId, Long orderId, Long reviewId, Integer score, String content) {
    ReviewUpdateValues updateValues = validateReviewUpdateValues(score, content);
    Review review = getReviewInOrder(orderId, reviewId);
    validateReviewer(review, reviewerId);

    review.update(updateValues.score(), updateValues.content());
    // updatedAt 응답값을 감사 필드 기준으로 확정한다.
    reviewRepository.flush();
    return UpdateReviewResponse.from(review);
  }

  /**
   * 리뷰 작성자 본인만 같은 주문에 속한 리뷰를 실제 삭제할 수 있다.
   *
   * @throws BusinessException REVIEW_NOT_FOUND - 리뷰가 없거나 주문과 리뷰가 일치하지 않는 경우
   * @throws BusinessException REVIEW_ACCESS_DENIED - 리뷰 작성자가 아닌 사용자의 요청
   */
  @Transactional
  public DeleteReviewResponse deleteReview(Long reviewerId, Long orderId, Long reviewId) {
    Review review = getReviewInOrder(orderId, reviewId);
    validateReviewer(review, reviewerId);

    reviewRepository.delete(review);
    return DeleteReviewResponse.success();
  }

  private ReviewUpdateValues validateReviewUpdateValues(Integer score, String content) {
    boolean hasScore = score != null;
    boolean hasContent = content != null;
    if (!hasScore && !hasContent) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    if (hasScore) {
      validateScore(score);
    }
    return new ReviewUpdateValues(score, normalizeUpdatedContent(content, hasContent));
  }

  private Review getReviewInOrder(Long orderId, Long reviewId) {
    return reviewRepository
        .findByIdAndOrderId(reviewId, orderId)
        .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
  }

  private void validateReviewer(Review review, Long reviewerId) {
    if (!Objects.equals(review.getReviewer().getId(), reviewerId)) {
      throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
    }
  }

  private void validateScore(Integer score) {
    if (score == null || score < 1 || score > 5) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }

  private String normalizeContent(String content) {
    if (content == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedContent = content.trim();
    if (normalizedContent.isBlank() || normalizedContent.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedContent;
  }

  private String normalizeUpdatedContent(String content, boolean hasContent) {
    if (!hasContent) {
      return null;
    }
    return normalizeContent(content);
  }

  private void validatePagination(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.INVALID_PAGINATION);
    }
  }

  private void validateUserExists(Long userId) {
    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
  }

  private Sort reviewSort() {
    return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
  }

  private record ReviewUpdateValues(Integer score, String content) {}

  private enum ReviewListStatus {
    WRITTEN,
    RECEIVED;

    private static ReviewListStatus from(String status) {
      if ("written".equals(status)) {
        return WRITTEN;
      }
      if ("received".equals(status)) {
        return RECEIVED;
      }
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }
}
