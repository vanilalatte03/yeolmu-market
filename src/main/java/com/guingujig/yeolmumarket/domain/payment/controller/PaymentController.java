package com.guingujig.yeolmumarket.domain.payment.controller;

import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.CancelPaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentDetailResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentStatusResponse;
import com.guingujig.yeolmumarket.domain.payment.service.CancelPaymentCommand;
import com.guingujig.yeolmumarket.domain.payment.service.PaymentFacade;
import com.guingujig.yeolmumarket.domain.payment.service.ProcessPaymentCommand;
import com.guingujig.yeolmumarket.domain.payment.service.ProcessPaymentResult;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentFacade paymentFacade;

  @PostMapping("/api/orders/{orderId}/payment")
  public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long orderId,
      @Size(max = 100) @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey,
      @Valid @RequestBody CreatePaymentRequest request) {
    ProcessPaymentResult result =
        paymentFacade.processPayment(
            new ProcessPaymentCommand(
                authenticatedUser.userId(), orderId, idempotencyKey, request));
    if (result.created()) {
      return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result.response()));
    }
    return ResponseEntity.ok(ApiResponse.success(result.response()));
  }

  @GetMapping("/api/payments/{paymentId}/status")
  public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long paymentId) {
    PaymentStatusResponse response =
        paymentFacade.getPaymentStatus(authenticatedUser.userId(), paymentId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/api/payments/{paymentId}")
  public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentDetail(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long paymentId) {
    PaymentDetailResponse response =
        paymentFacade.getPaymentDetail(authenticatedUser.userId(), paymentId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/payments/{paymentId}/cancel")
  public ResponseEntity<ApiResponse<CancelPaymentResponse>> cancelPayment(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long paymentId,
      @RequestBody(required = false) CancelPaymentRequest request) {
    String reason = resolveCancelReason(request);
    CancelPaymentResponse response =
        paymentFacade.cancelPayment(
            new CancelPaymentCommand(authenticatedUser.userId(), paymentId, reason));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private String resolveCancelReason(CancelPaymentRequest request) {
    if (request == null) {
      return null;
    }
    return request.reason();
  }
}
