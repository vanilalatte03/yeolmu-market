package com.guingujig.yeolmumarket.domain.order.controller;

import com.guingujig.yeolmumarket.domain.order.dto.CreateOrderResponse;
import com.guingujig.yeolmumarket.domain.order.service.OrderService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class OrderController {

  private final OrderService orderService;

  @PostMapping("/{productId}/orders")
  public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    CreateOrderResponse response = orderService.createOrder(authenticatedUser.userId(), productId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }
}
