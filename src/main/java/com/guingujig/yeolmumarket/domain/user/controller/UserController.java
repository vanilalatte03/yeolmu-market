package com.guingujig.yeolmumarket.domain.user.controller;

import com.guingujig.yeolmumarket.domain.user.dto.GetUserResponse;
import com.guingujig.yeolmumarket.domain.user.service.UserService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  @GetMapping("/{userId}")
  public ResponseEntity<ApiResponse<GetUserResponse>> getUser(@PathVariable Long userId) {
    return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId)));
  }
}
