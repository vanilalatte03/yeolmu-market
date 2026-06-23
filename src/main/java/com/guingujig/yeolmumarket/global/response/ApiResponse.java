package com.guingujig.yeolmumarket.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 모든 REST API가 사용하는 공통 응답 wrapper다.
 *
 * <p>{@code data}와 {@code errors}는 값이 없으면 JSON에서 생략해 {@code docs/API.md}의 공통 응답 계약을 맞춘다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, String code, String message, T data, List<String> errors) {

  private static final String SUCCESS_CODE = "SUCCESS";
  private static final String SUCCESS_MESSAGE = "요청이 성공했습니다.";

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data, null);
  }

  /** 응답 본문에 별도 데이터가 없는 성공 결과에 사용한다. */
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
