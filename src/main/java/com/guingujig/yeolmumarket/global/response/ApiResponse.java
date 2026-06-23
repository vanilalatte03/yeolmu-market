package com.guingujig.yeolmumarket.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, String code, String message, T data, List<String> errors) {

  private static final String SUCCESS_CODE = "SUCCESS";
  private static final String SUCCESS_MESSAGE = "요청이 성공했습니다.";

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data, null);
  }

  public static ApiResponse<Void> emptySuccess() {
    return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, null, null);
  }

  public static ApiResponse<Void> failure(String code, String message) {
    return new ApiResponse<>(false, code, message, null, null);
  }

  public static ApiResponse<Void> failure(String code, String message, List<String> errors) {
    return new ApiResponse<>(false, code, message, null, errors);
  }
}
