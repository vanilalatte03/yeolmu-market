package com.guingujig.yeolmumarket.domain.refund.controller;

import com.guingujig.yeolmumarket.domain.refund.dto.ApproveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequest;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequest;
import com.guingujig.yeolmumarket.domain.refund.dto.RejectRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequest;
import com.guingujig.yeolmumarket.domain.refund.dto.ResolveRefundRequestResponse;
import com.guingujig.yeolmumarket.domain.refund.service.RefundService;
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
public class RefundController {

  private final RefundService refundService;

  @PostMapping("/api/orders/{orderId}/refund")
  public ResponseEntity<ApiResponse<CreateRefundRequestResponse>> createRefundRequest(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long orderId,
      @Valid @RequestBody CreateRefundRequest request) {
    CreateRefundRequestResponse response =
        refundService.createRefundRequest(authenticatedUser.userId(), orderId, request.reason());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/api/refund/{refundId}/approve")
  public ResponseEntity<ApiResponse<ApproveRefundRequestResponse>> approveRefundRequest(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long refundId) {
    ApproveRefundRequestResponse response =
        refundService.approveRefundRequest(authenticatedUser.userId(), refundId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/refund/{refundId}/reject")
  public ResponseEntity<ApiResponse<RejectRefundRequestResponse>> rejectRefundRequest(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long refundId,
      @RequestBody(required = false) RejectRefundRequest request) {
    String reason = request == null ? null : request.reason();
    RejectRefundRequestResponse response =
        refundService.rejectRefundRequest(authenticatedUser.userId(), refundId, reason);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/refund/{refundId}/resolve")
  public ResponseEntity<ApiResponse<ResolveRefundRequestResponse>> resolveRefundRequest(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long refundId,
      @Valid @RequestBody ResolveRefundRequest request) {
    ResolveRefundRequestResponse response =
        refundService.resolveRefundRequest(
            authenticatedUser.userId(), refundId, request.resolution(), request.reason());
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
