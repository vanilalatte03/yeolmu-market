package com.guingujig.yeolmumarket.domain.auth.controller;

import com.guingujig.yeolmumarket.domain.auth.dto.LoginRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.LoginResponse;
import com.guingujig.yeolmumarket.domain.auth.dto.LogoutResponse;
import com.guingujig.yeolmumarket.domain.auth.dto.RefreshTokenRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.RefreshTokenResponse;
import com.guingujig.yeolmumarket.domain.auth.dto.SignupRequest;
import com.guingujig.yeolmumarket.domain.auth.dto.SignupResponse;
import com.guingujig.yeolmumarket.domain.auth.service.AuthService;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import com.guingujig.yeolmumarket.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    SignupResponse response = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
      @Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request)));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<LogoutResponse>> logout(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    String token = authorization.substring("Bearer ".length());
    authService.logout(authenticatedUser.userId(), token);
    return ResponseEntity.ok(ApiResponse.success(new LogoutResponse(true)));
  }
}
