package com.guingujig.yeolmumarket.domain.payment.controller;

import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;
import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;
import com.guingujig.yeolmumarket.domain.payment.service.PaymentService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping("/api/orders/{orderId}/payment")
  public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @PathVariable Long orderId,
      @Size(max = 100) @RequestHeader(value = "Idempotency-Key", required = false)
          String idempotencyKey,
      @Valid @RequestBody CreatePaymentRequest request) {
    PaymentService.ProcessPaymentResult result =
        paymentService.processPayment(authenticatedUser.userId(), orderId, idempotencyKey, request);
    if (result.created()) {
      return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result.response()));
    }
    return ResponseEntity.ok(ApiResponse.success(result.response()));
  }
}
