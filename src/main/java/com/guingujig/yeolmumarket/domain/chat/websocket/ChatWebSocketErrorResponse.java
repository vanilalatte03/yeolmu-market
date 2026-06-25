package com.guingujig.yeolmumarket.domain.chat.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatWebSocketErrorResponse(String code, String message, Long roomId) {

  public static ChatWebSocketErrorResponse of(ErrorCode errorCode) {
    return new ChatWebSocketErrorResponse(errorCode.name(), errorCode.getMessage(), null);
  }

  public static ChatWebSocketErrorResponse of(ErrorCode errorCode, Long roomId) {
    return new ChatWebSocketErrorResponse(errorCode.name(), errorCode.getMessage(), roomId);
  }
}
