package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;

public class ChatWebSocketAuthenticationException extends RuntimeException {

  private final ErrorCode errorCode;

  ChatWebSocketAuthenticationException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
