package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException, ServletException {
    ErrorCode errorCode =
        resolveErrorCode(request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE));
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  private ErrorCode resolveErrorCode(Object jwtErrorAttribute) {
    if (jwtErrorAttribute instanceof ErrorCode errorCode) {
      return errorCode;
    }
    return ErrorCode.UNAUTHORIZED;
  }
}
