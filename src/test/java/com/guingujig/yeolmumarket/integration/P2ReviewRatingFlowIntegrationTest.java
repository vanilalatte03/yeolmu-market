package com.guingujig.yeolmumarket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class P2ReviewRatingFlowIntegrationTest extends IntegrationTestSupport {

  @Test
  void P2_거래완료_후_양방향_리뷰와_평점_조회_수정_삭제가_정상_동작한다() throws Exception {
    PaidTransactionFixture fixture = createCompletedOrderFixture();

    Long buyerReviewId =
        createReview(
            fixture.buyer(),
            fixture.orderId(),
            fixture.seller().userId(),
            5,
            "상품 설명이 정확하고 응답이 빨랐습니다.");
    Long sellerReviewId =
        createReview(
            fixture.seller(),
            fixture.orderId(),
            fixture.buyer().userId(),
            4,
            "약속 시간을 잘 지킨 구매자입니다.");

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", fixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, fixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 5, "content", "중복 리뷰입니다."))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

    assertMyWrittenReview(fixture.buyer(), buyerReviewId, fixture.orderId(), fixture.seller());
    assertMyReceivedReview(fixture.seller(), buyerReviewId, fixture.orderId(), fixture.buyer());
    assertPublicReceivedReview(fixture.seller(), buyerReviewId, fixture.buyer());
    assertUserRating(fixture.seller().userId(), 5.0, 1);
    assertUserRating(fixture.buyer().userId(), 4.0, 1);
    assertProductSellerRating(fixture.productId(), 5.0);

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/reviews/{reviewId}", fixture.orderId(), buyerReviewId)
                .header(HttpHeaders.AUTHORIZATION, fixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 3, "content", "응답은 빨랐지만 포장이 아쉬웠습니다."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.reviewId").value(buyerReviewId))
        .andExpect(jsonPath("$.data.score").value(3))
        .andExpect(jsonPath("$.data.content").value("응답은 빨랐지만 포장이 아쉬웠습니다."))
        .andExpect(jsonPath("$.data.updatedAt", matchesPattern(UTC_OFFSET_PATTERN)));

    assertThat(reviewRepository.findById(buyerReviewId).orElseThrow().getScore()).isEqualTo(3);
    assertUserRating(fixture.seller().userId(), 3.0, 1);
    assertProductSellerRating(fixture.productId(), 3.0);

    mockMvc
        .perform(
            delete("/api/orders/{orderId}/reviews/{reviewId}", fixture.orderId(), sellerReviewId)
                .header(HttpHeaders.AUTHORIZATION, fixture.seller().authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.deleted").value(true));

    assertThat(reviewRepository.findById(sellerReviewId)).isEmpty();
    assertUserRating(fixture.buyer().userId(), 0.0, 0);
  }

  @Test
  void P2_리뷰_실패_흐름은_계약된_에러코드를_반환하고_리뷰를_변경하지_않는다() throws Exception {
    PaidTransactionFixture shippingFixture = createShippingOrderFixture();

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", shippingFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, shippingFixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 5, "content", "거래 완료 전 리뷰입니다."))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

    PaidTransactionFixture completedFixture = createCompletedOrderFixture();
    TestUser other = signupAndLogin("review-other", "리뷰타인");

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", completedFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, other.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 5, "content", "주문 참여자가 아닌 리뷰입니다."))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", completedFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, completedFixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 0, "content", "잘못된 평점입니다."))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/orders/{orderId}/reviews", completedFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, completedFixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 5, "content", "   "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    Long reviewId =
        createReview(
            completedFixture.buyer(),
            completedFixture.orderId(),
            completedFixture.seller().userId(),
            5,
            "정상 리뷰입니다.");

    mockMvc
        .perform(
            patch("/api/orders/{orderId}/reviews/{reviewId}", completedFixture.orderId(), reviewId)
                .header(HttpHeaders.AUTHORIZATION, other.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("score", 4))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));

    mockMvc
        .perform(
            delete("/api/orders/{orderId}/reviews/{reviewId}", completedFixture.orderId(), reviewId)
                .header(HttpHeaders.AUTHORIZATION, other.authorization()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));

    mockMvc
        .perform(
            get("/api/users/me/reviews")
                .header(HttpHeaders.AUTHORIZATION, completedFixture.buyer().authorization())
                .param("status", "all"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertThat(reviewRepository.findById(reviewId)).isPresent();
    assertThat(reviewRepository.findById(reviewId).orElseThrow().getScore()).isEqualTo(5);
  }

  private Long createReview(
      TestUser reviewer, Long orderId, Long expectedRevieweeId, Integer score, String content)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/orders/{orderId}/reviews", orderId)
                    .header(HttpHeaders.AUTHORIZATION, reviewer.authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("score", score, "content", " " + content + " "))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reviewId").isNumber())
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.reviewerId").value(reviewer.userId()))
            .andExpect(jsonPath("$.data.revieweeId").value(expectedRevieweeId))
            .andExpect(jsonPath("$.data.score").value(score))
            .andExpect(jsonPath("$.data.content").value(content))
            .andExpect(jsonPath("$.data.createdAt", matchesPattern(UTC_OFFSET_PATTERN)))
            .andReturn();

    return readBody(result).requiredAt("/data/reviewId").longValue();
  }

  private void assertMyWrittenReview(TestUser user, Long reviewId, Long orderId, TestUser reviewee)
      throws Exception {
    mockMvc
        .perform(
            get("/api/users/me/reviews")
                .header(HttpHeaders.AUTHORIZATION, user.authorization())
                .param("status", "written")
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId))
        .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
        .andExpect(jsonPath("$.data.content[0].revieweeNickname").value(reviewee.nickname()))
        .andExpect(jsonPath("$.data.content[0].reviewerNickname").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].createdAt", matchesPattern(UTC_OFFSET_PATTERN)))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  private void assertMyReceivedReview(TestUser user, Long reviewId, Long orderId, TestUser reviewer)
      throws Exception {
    mockMvc
        .perform(
            get("/api/users/me/reviews")
                .header(HttpHeaders.AUTHORIZATION, user.authorization())
                .param("status", "received"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId))
        .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
        .andExpect(jsonPath("$.data.content[0].reviewerNickname").value(reviewer.nickname()))
        .andExpect(jsonPath("$.data.content[0].revieweeNickname").doesNotExist())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  private void assertPublicReceivedReview(TestUser user, Long reviewId, TestUser reviewer)
      throws Exception {
    mockMvc
        .perform(
            get("/api/users/{userId}/reviews", user.userId())
                .param("page", "0")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId))
        .andExpect(jsonPath("$.data.content[0].orderId").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].reviewerNickname").value(reviewer.nickname()))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  private void assertUserRating(Long userId, Double averageRating, int reviewCount)
      throws Exception {
    mockMvc
        .perform(get("/api/users/{userId}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(userId))
        .andExpect(jsonPath("$.data.averageRating").value(averageRating))
        .andExpect(jsonPath("$.data.reviewCount").value(reviewCount));
  }

  private void assertProductSellerRating(Long productId, Double averageRating) throws Exception {
    mockMvc
        .perform(get("/api/products/{productId}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.productId").value(productId))
        .andExpect(jsonPath("$.data.seller.averageRating").value(averageRating));
  }
}
