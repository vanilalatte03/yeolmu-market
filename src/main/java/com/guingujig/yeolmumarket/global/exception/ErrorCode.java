package com.guingujig.yeolmumarket.global.exception;

import org.springframework.http.HttpStatus;

/**
 * API 실패 응답에 사용할 에러 코드 카탈로그다.
 *
 * <p>각 코드는 HTTP 상태와 기본 메시지를 함께 가져, 예외 처리와 문서의 에러 계약이 같은 값을 참조하게 한다.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 본문, 쿼리 파라미터, 경로 변수 검증에 실패했습니다."),
  INVALID_ENUM_VALUE(HttpStatus.BAD_REQUEST, "허용하지 않는 Enum 값입니다."),
  MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "필수 값이 누락되었습니다."),
  INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "페이지 번호 또는 크기가 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "잘못된 JWT입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 JWT입니다."),
  REVOKED_TOKEN(HttpStatus.UNAUTHORIZED, "폐기된 JWT입니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP method입니다."),
  CONFLICT(HttpStatus.CONFLICT, "현재 상태와 충돌하는 요청입니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  INVALID_LOGIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
  PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "상품에 대한 권한이 없습니다."),
  PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품이 아닙니다."),
  PRODUCT_HAS_ACTIVE_ORDER(HttpStatus.CONFLICT, "거래 진행 중인 상품입니다."),
  CANNOT_ORDER_OWN_PRODUCT(HttpStatus.BAD_REQUEST, "자신의 상품은 주문할 수 없습니다."),
  IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 이미지를 찾을 수 없습니다."),
  UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
  FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "업로드 파일 크기를 초과했습니다."),
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
  ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "주문에 대한 권한이 없습니다."),
  INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서 수행할 수 없는 작업입니다."),
  ORDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 상품에 이미 유효한 주문이 존재합니다."),
  CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
  CHAT_ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다."),
  MESSAGE_SEND_NOT_ALLOWED(HttpStatus.FORBIDDEN, "메시지를 보낼 수 없는 채팅방입니다."),
  CANNOT_CHAT_OWN_PRODUCT(HttpStatus.BAD_REQUEST, "자신의 상품에는 채팅방을 만들 수 없습니다."),
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
  PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "결제에 대한 권한이 없습니다."),
  PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 주문의 결제가 이미 존재합니다."),
  INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "현재 결제 상태에서 수행할 수 없는 작업입니다."),
  CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
  CATEGORY_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 카테고리명입니다."),
  CATEGORY_IN_USE(HttpStatus.CONFLICT, "상품이 연결된 카테고리는 삭제할 수 없습니다."),
  WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "찜을 찾을 수 없습니다."),
  WISH_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 찜한 상품입니다."),
  REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
  REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "리뷰에 대한 권한이 없습니다."),
  REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 작성한 주문 리뷰입니다."),
  REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT, "리뷰를 작성할 수 없는 주문 상태입니다.");

  private final HttpStatus httpStatus;
  private final String message;

  ErrorCode(HttpStatus httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getMessage() {
    return message;
  }
}
