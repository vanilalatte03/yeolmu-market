package com.guingujig.yeolmumarket.global.exception;

import com.guingujig.yeolmumarket.global.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException exception) {
    List<String> errors = extractValidationErrors(exception);

    ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage(), errors));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception) {
    List<String> errors =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                result.getMethodParameter().getParameterName()
                                    + ": "
                                    + error.getDefaultMessage()))
            .toList();

    ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage(), errors));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
      ConstraintViolationException exception) {
    List<String> errors =
        exception.getConstraintViolations().stream().map(this::formatConstraintViolation).toList();

    ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage(), errors));
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
    ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException exception) {
    ErrorCode errorCode =
        exception.getRequiredType() != null && exception.getRequiredType().isEnum()
            ? ErrorCode.INVALID_ENUM_VALUE
            : ErrorCode.VALIDATION_FAILED;

    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
      NoResourceFoundException exception) {
    ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException exception) {
    ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
    log.error("처리되지 않은 예외가 발생했습니다.", exception);
    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  private List<String> extractValidationErrors(MethodArgumentNotValidException exception) {
    List<String> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream().map(this::formatFieldError).toList();
    if (!fieldErrors.isEmpty()) {
      return fieldErrors;
    }
    return exception.getBindingResult().getGlobalErrors().stream()
        .map(this::formatObjectError)
        .toList();
  }

  private String formatFieldError(FieldError fieldError) {
    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
  }

  private String formatObjectError(ObjectError objectError) {
    return objectError.getObjectName() + ": " + objectError.getDefaultMessage();
  }

  private String formatConstraintViolation(ConstraintViolation<?> violation) {
    String field = null;
    for (Path.Node node : violation.getPropertyPath()) {
      field = node.getName();
    }
    return field + ": " + violation.getMessage();
  }
}
