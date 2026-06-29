package com.guingujig.yeolmumarket.domain.refund.controller;

import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestRequest;
import com.guingujig.yeolmumarket.domain.refund.dto.CreateRefundRequestResponse;
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
      @Valid @RequestBody CreateRefundRequestRequest request) {
    CreateRefundRequestResponse response =
        refundService.createRefundRequest(authenticatedUser.userId(), orderId, request.reason());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}
