package com.guingujig.yeolmumarket.domain.order.controller;

import com.guingujig.yeolmumarket.domain.order.dto.CancelOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.dto.GetOrderResponse;
import com.guingujig.yeolmumarket.domain.order.service.OrderService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping("/api/products/{productId}/orders")
  public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    CreateOrderResponse response = orderService.createOrder(authenticatedUser.userId(), productId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/api/orders/{orderId}/cancel")
  public ResponseEntity<ApiResponse<CancelOrderResponse>> cancelOrder(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long orderId) {
    CancelOrderResponse response = orderService.cancelOrder(authenticatedUser.userId(), orderId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/api/orders/{orderId}")
  public ResponseEntity<ApiResponse<GetOrderResponse>> getOrder(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long orderId) {
    GetOrderResponse response = orderService.getOrder(authenticatedUser.userId(), orderId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
