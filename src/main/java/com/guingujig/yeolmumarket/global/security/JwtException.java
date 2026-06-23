package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;

class JwtException extends RuntimeException {

  private final ErrorCode errorCode;

  JwtException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  ErrorCode getErrorCode() {
    return errorCode;
  }
}
