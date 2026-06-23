package com.guingujig.yeolmumarket.global.exception;

/**
 * 도메인 서비스에서 비즈니스 규칙 위반을 표현할 때 사용하는 공통 예외다.
 *
 * <p>{@link ErrorCode}를 함께 보관해 전역 예외 처리기가 HTTP 상태와 응답 코드를 일관되게 결정할 수 있게 한다.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
