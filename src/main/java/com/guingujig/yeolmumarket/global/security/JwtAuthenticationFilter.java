package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import com.guingujig.yeolmumarket.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  public static final String JWT_ERROR_ATTRIBUTE = "JWT_ERROR_CODE";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtAuthenticationService jwtAuthenticationService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null) {
      try {
        Authentication auth = jwtAuthenticationService.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (JwtException e) {
        request.setAttribute(JWT_ERROR_ATTRIBUTE, e.getErrorCode());
      } catch (BusinessException e) {
        request.setAttribute(JWT_ERROR_ATTRIBUTE, e.getErrorCode());
      } catch (DataAccessException e) {
        writeError(response, ErrorCode.REDIS_UNAVAILABLE);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiResponse.failure(errorCode.name(), errorCode.getMessage()));
  }

  private String resolveToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return authorization.substring(BEARER_PREFIX.length());
  }
}
