package com.guingujig.yeolmumarket.domain.user.controller;

import com.guingujig.yeolmumarket.domain.order.dto.MyOrderListItemResponse;
import com.guingujig.yeolmumarket.domain.order.dto.MySaleListItemResponse;
import com.guingujig.yeolmumarket.domain.order.entity.OrderStatus;
import com.guingujig.yeolmumarket.domain.order.service.OrderFacade;
import com.guingujig.yeolmumarket.domain.user.dto.GetUserResponse;
import com.guingujig.yeolmumarket.domain.user.dto.UpdateUserRequest;
import com.guingujig.yeolmumarket.domain.user.dto.UpdateUserResponse;
import com.guingujig.yeolmumarket.domain.user.service.UserService;
import com.guingujig.yeolmumarket.domain.wish.dto.WishListItemResponse;
import com.guingujig.yeolmumarket.domain.wish.service.WishFacade;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.response.PageResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final OrderFacade orderFacade;
  private final WishFacade wishFacade;

  @GetMapping("/{userId}")
  public ResponseEntity<ApiResponse<GetUserResponse>> getUser(@PathVariable Long userId) {
    return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId)));
  }

  @PutMapping("/me")
  public ResponseEntity<ApiResponse<UpdateUserResponse>> updateMe(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @Valid @RequestBody UpdateUserRequest request) {
    UpdateUserResponse response = userService.updateMe(authenticatedUser.userId(), request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me/orders")
  public ResponseEntity<ApiResponse<PageResponse<MyOrderListItemResponse>>> getMyOrders(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) OrderStatus status) {
    PageResponse<MyOrderListItemResponse> response =
        orderFacade.getMyOrders(authenticatedUser.userId(), page, size, status);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me/sales")
  public ResponseEntity<ApiResponse<PageResponse<MySaleListItemResponse>>> getMySales(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) OrderStatus status) {
    PageResponse<MySaleListItemResponse> response =
        orderFacade.getMySales(authenticatedUser.userId(), page, size, status);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me/wishes")
  public ResponseEntity<ApiResponse<PageResponse<WishListItemResponse>>> getMyWishes(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    PageResponse<WishListItemResponse> response =
        wishFacade.getMyWishes(authenticatedUser.userId(), page, size);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
