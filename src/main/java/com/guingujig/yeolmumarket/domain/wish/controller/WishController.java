package com.guingujig.yeolmumarket.domain.wish.controller;

import com.guingujig.yeolmumarket.domain.wish.dto.WishResponse;
import com.guingujig.yeolmumarket.domain.wish.service.WishService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products/{productId}/wishes")
public class WishController {

  private final WishService wishService;

  @PostMapping
  public ResponseEntity<ApiResponse<WishResponse>> createWish(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    WishResponse response = wishService.createWish(authenticatedUser.userId(), productId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @DeleteMapping
  public ResponseEntity<ApiResponse<WishResponse>> deleteWish(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable Long productId) {
    WishResponse response = wishService.deleteWish(authenticatedUser.userId(), productId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
