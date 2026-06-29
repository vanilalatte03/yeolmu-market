package com.guingujig.yeolmumarket.integration;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.payment.entity.PaymentStatus;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.refund.entity.RefundRequestStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class P2RefundDisputeFlowIntegrationTest extends IntegrationTestSupport {

  @Test
  void P2_환불_승인_흐름은_주문_결제_상품_환불요청_상태를_변경한다() throws Exception {
    PaidTransactionFixture fixture = createShippingOrderFixture();

    Long refundRequestId =
        createRefundRequest(fixture.buyer(), fixture.orderId(), "상품 상태가 설명과 다릅니다.");

    assertOrderAndProductStatus(
        fixture.orderId(), OrderStatus.REFUND_REQUESTED, ProductStatus.RESERVED);
    assertPaymentStatus(fixture.paymentId(), PaymentStatus.PAID);
    assertRefundRequestStatus(refundRequestId, RefundRequestStatus.REQUESTED);

    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", refundRequestId)
                .header(HttpHeaders.AUTHORIZATION, fixture.seller().authorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequestId))
        .andExpect(jsonPath("$.data.orderId").value(fixture.orderId()))
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.approvedAt", matchesPattern(UTC_OFFSET_PATTERN)));

    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.REFUNDED, ProductStatus.ON_SALE);
    assertPaymentStatus(fixture.paymentId(), PaymentStatus.REFUNDED);
    assertRefundRequestStatus(refundRequestId, RefundRequestStatus.APPROVED);
  }

  @Test
  void P2_분쟁_REFUND_종료_흐름은_환불로_거래를_종료한다() throws Exception {
    PaidTransactionFixture fixture = createDisputedRefundFixture();
    Long refundRequestId =
        refundRequestRepository.findByOrder_Id(fixture.orderId()).orElseThrow().getId();

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequestId)
                .header(HttpHeaders.AUTHORIZATION, fixture.seller().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resolution", "REFUND", "reason", "구매자 환불로 종료합니다."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequestId))
        .andExpect(jsonPath("$.data.orderId").value(fixture.orderId()))
        .andExpect(jsonPath("$.data.status").value("CLOSED"))
        .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
        .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"))
        .andExpect(jsonPath("$.data.resolvedAt", matchesPattern(UTC_OFFSET_PATTERN)));

    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.REFUNDED, ProductStatus.ON_SALE);
    assertPaymentStatus(fixture.paymentId(), PaymentStatus.REFUNDED);
    assertRefundRequestStatus(refundRequestId, RefundRequestStatus.CLOSED);
  }

  @Test
  void P2_분쟁_COMPLETE_종료_흐름은_거래완료로_분쟁을_종료한다() throws Exception {
    PaidTransactionFixture fixture = createDisputedRefundFixture();
    Long refundRequestId =
        refundRequestRepository.findByOrder_Id(fixture.orderId()).orElseThrow().getId();

    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", refundRequestId)
                .header(HttpHeaders.AUTHORIZATION, fixture.seller().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resolution", "COMPLETE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequestId))
        .andExpect(jsonPath("$.data.orderId").value(fixture.orderId()))
        .andExpect(jsonPath("$.data.status").value("CLOSED"))
        .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"))
        .andExpect(jsonPath("$.data.resolvedAt", matchesPattern(UTC_OFFSET_PATTERN)));

    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.COMPLETED, ProductStatus.SOLD_OUT);
    assertPaymentStatus(fixture.paymentId(), PaymentStatus.PAID);
    assertRefundRequestStatus(refundRequestId, RefundRequestStatus.CLOSED);
  }

  @Test
  void P2_환불_분쟁_실패_흐름은_계약된_에러코드를_반환하고_상태를_보존한다() throws Exception {
    TransactionFixture createdFixture = createCreatedOrderFixture();
    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", createdFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, createdFixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "배송 전 환불 요청"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
    assertOrderAndProductStatus(
        createdFixture.orderId(), OrderStatus.CREATED, ProductStatus.RESERVED);

    PaidTransactionFixture accessFixture = createShippingOrderFixture();
    TestUser other = signupAndLogin("refund-other", "환불타인");
    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", accessFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, other.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "타인의 환불 요청"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
    assertOrderAndProductStatus(
        accessFixture.orderId(), OrderStatus.SHIPPING, ProductStatus.RESERVED);

    PaidTransactionFixture duplicateFixture = createShippingOrderFixture();
    Long duplicateRefundId =
        createRefundRequest(duplicateFixture.buyer(), duplicateFixture.orderId(), "첫 환불 요청");
    mockMvc
        .perform(
            post("/api/orders/{orderId}/refund", duplicateFixture.orderId())
                .header(HttpHeaders.AUTHORIZATION, duplicateFixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "두 번째 환불 요청"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ALREADY_EXISTS"));
    assertRefundRequestStatus(duplicateRefundId, RefundRequestStatus.REQUESTED);

    PaidTransactionFixture sellerOnlyFixture = createShippingOrderFixture();
    Long sellerOnlyRefundId =
        createRefundRequest(
            sellerOnlyFixture.buyer(), sellerOnlyFixture.orderId(), "판매자만 처리할 수 있습니다.");
    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", sellerOnlyRefundId)
                .header(HttpHeaders.AUTHORIZATION, sellerOnlyFixture.buyer().authorization()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", sellerOnlyRefundId)
                .header(HttpHeaders.AUTHORIZATION, other.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "권한 없는 거절"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
    assertRefundRequestStatus(sellerOnlyRefundId, RefundRequestStatus.REQUESTED);
    assertOrderAndProductStatus(
        sellerOnlyFixture.orderId(), OrderStatus.REFUND_REQUESTED, ProductStatus.RESERVED);

    rejectRefundRequest(
        sellerOnlyFixture.seller(),
        sellerOnlyRefundId,
        "정상 상품이라 판매자는 거절합니다.",
        sellerOnlyFixture.orderId());
    assertRefundRequestStatus(sellerOnlyRefundId, RefundRequestStatus.DISPUTED);
    mockMvc
        .perform(
            post("/api/refund/{refundId}/approve", sellerOnlyRefundId)
                .header(HttpHeaders.AUTHORIZATION, sellerOnlyFixture.seller().authorization()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REFUND_REQUEST_STATUS"));
    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", sellerOnlyRefundId)
                .header(HttpHeaders.AUTHORIZATION, sellerOnlyFixture.seller().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "이미 분쟁 상태입니다."))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REFUND_REQUEST_STATUS"));

    PaidTransactionFixture resolveStatusFixture = createShippingOrderFixture();
    Long requestedRefundId =
        createRefundRequest(
            resolveStatusFixture.buyer(), resolveStatusFixture.orderId(), "아직 요청 상태입니다.");
    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", requestedRefundId)
                .header(HttpHeaders.AUTHORIZATION, resolveStatusFixture.seller().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resolution", "REFUND"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REFUND_REQUEST_STATUS"));
    assertRefundRequestStatus(requestedRefundId, RefundRequestStatus.REQUESTED);

    PaidTransactionFixture resolveAccessFixture = createDisputedRefundFixture();
    Long disputedRefundId =
        refundRequestRepository
            .findByOrder_Id(resolveAccessFixture.orderId())
            .orElseThrow()
            .getId();
    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", disputedRefundId)
                .header(HttpHeaders.AUTHORIZATION, resolveAccessFixture.buyer().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resolution", "REFUND"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
    mockMvc
        .perform(
            post("/api/refund/{refundId}/resolve", disputedRefundId)
                .header(HttpHeaders.AUTHORIZATION, resolveAccessFixture.seller().authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("resolution", "INVALID"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertRefundRequestStatus(disputedRefundId, RefundRequestStatus.DISPUTED);
    assertOrderAndProductStatus(
        resolveAccessFixture.orderId(), OrderStatus.DISPUTED, ProductStatus.RESERVED);
    assertPaymentStatus(resolveAccessFixture.paymentId(), PaymentStatus.PAID);
  }

  private PaidTransactionFixture createDisputedRefundFixture() throws Exception {
    PaidTransactionFixture fixture = createShippingOrderFixture();
    Long refundRequestId = createRefundRequest(fixture.buyer(), fixture.orderId(), "상품에 문제가 있습니다.");
    rejectRefundRequest(
        fixture.seller(), refundRequestId, "판매자는 정상 상품으로 판단합니다.", fixture.orderId());
    assertOrderAndProductStatus(fixture.orderId(), OrderStatus.DISPUTED, ProductStatus.RESERVED);
    assertPaymentStatus(fixture.paymentId(), PaymentStatus.PAID);
    assertRefundRequestStatus(refundRequestId, RefundRequestStatus.DISPUTED);
    return fixture;
  }

  private Long createRefundRequest(TestUser buyer, Long orderId, String reason) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/orders/{orderId}/refund", orderId)
                    .header(HttpHeaders.AUTHORIZATION, buyer.authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("reason", " " + reason + " "))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.refundRequestId").isNumber())
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.status").value("REQUESTED"))
            .andExpect(jsonPath("$.data.orderStatus").value("REFUND_REQUESTED"))
            .andExpect(jsonPath("$.data.requestedAt", matchesPattern(UTC_OFFSET_PATTERN)))
            .andReturn();

    return readBody(result).requiredAt("/data/refundRequestId").longValue();
  }

  private void rejectRefundRequest(
      TestUser seller, Long refundRequestId, String reason, Long orderId) throws Exception {
    mockMvc
        .perform(
            post("/api/refund/{refundId}/reject", refundRequestId)
                .header(HttpHeaders.AUTHORIZATION, seller.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", " " + reason + " "))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.refundRequestId").value(refundRequestId))
        .andExpect(jsonPath("$.data.orderId").value(orderId))
        .andExpect(jsonPath("$.data.status").value("DISPUTED"))
        .andExpect(jsonPath("$.data.orderStatus").value("DISPUTED"))
        .andExpect(jsonPath("$.data.rejectedAt", matchesPattern(UTC_OFFSET_PATTERN)));
  }
}
