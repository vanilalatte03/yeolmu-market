package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtTokenProvider {

  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final String SHA_256 = "SHA-256";
  private static final String JWT_ALGORITHM = "HS256";
  private static final String TOKEN_TYPE_ACCESS = "ACCESS";
  private static final String TOKEN_TYPE_REFRESH = "REFRESH";
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;
  private final byte[] secret;
  private final long accessTokenValiditySeconds;
  private final long refreshTokenValiditySeconds;

  public JwtTokenProvider(
      ObjectMapper objectMapper,
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
      @Value("${jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
    this.objectMapper = objectMapper;
    validateSecret(secret);
    validateValiditySeconds(accessTokenValiditySeconds, "access token");
    validateValiditySeconds(refreshTokenValiditySeconds, "refresh token");
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
  }

  public String issueAccessToken(User user) {
    return generateToken(user, TOKEN_TYPE_ACCESS, accessTokenValiditySeconds);
  }

  public String issueRefreshToken(User user) {
    return generateToken(user, TOKEN_TYPE_REFRESH, refreshTokenValiditySeconds);
  }

  /** 테스트 전용: 이미 만료된 토큰을 발급한다. */
  String issueExpiredAccessToken(User user) {
    return generateToken(user, TOKEN_TYPE_ACCESS, -1);
  }

  /** 테스트 전용: 이미 만료된 refresh token을 발급한다. */
  String issueExpiredRefreshToken(User user) {
    return generateToken(user, TOKEN_TYPE_REFRESH, -1);
  }

  public Duration getAccessTokenRemainingTtl(String token) {
    JwtClaims claims = parseClaims(token);
    if (!TOKEN_TYPE_ACCESS.equals(claims.tokenType())) {
      throw new JwtException(ErrorCode.INVALID_TOKEN, "access token이 아닙니다.");
    }
    long remaining = claims.expiresAtEpochSeconds() - Instant.now().getEpochSecond();
    if (remaining <= 0) {
      throw new JwtException(ErrorCode.EXPIRED_TOKEN, "JWT가 만료되었습니다.");
    }
    return Duration.ofSeconds(remaining);
  }

  public long getAccessTokenValiditySeconds() {
    return accessTokenValiditySeconds;
  }

  public long getRefreshTokenValiditySeconds() {
    return refreshTokenValiditySeconds;
  }

  public String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance(SHA_256);
      return BASE64_URL_ENCODER.encodeToString(
          digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("JWT 해시 생성에 실패했습니다.", exception);
    }
  }

  public Authentication getAuthentication(String token) {
    JwtClaims claims = parseClaims(token);
    if (!TOKEN_TYPE_ACCESS.equals(claims.tokenType())) {
      throw new JwtException(ErrorCode.INVALID_TOKEN, "access token이 아닙니다.");
    }
    AuthenticatedUser principal =
        new AuthenticatedUser(claims.userId(), claims.email(), claims.role());
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
  }

  public JwtRefreshClaims parseRefreshToken(String token) {
    JwtClaims claims = parseClaims(token);
    if (!TOKEN_TYPE_REFRESH.equals(claims.tokenType())) {
      throw new JwtException(ErrorCode.INVALID_TOKEN, "refresh token이 아닙니다.");
    }
    return new JwtRefreshClaims(claims.userId(), claims.jti(), claims.expiresAtEpochSeconds());
  }

  private String generateToken(User user, String tokenType, long validitySeconds) {
    Instant now = Instant.now();

    Map<String, Object> header = new LinkedHashMap<>();
    header.put("typ", "JWT");
    header.put("alg", JWT_ALGORITHM);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sub", user.getId().toString());
    payload.put("email", user.getEmail());
    payload.put("role", user.getRole().name());
    payload.put("tokenType", tokenType);
    payload.put("jti", UUID.randomUUID().toString());
    payload.put("iat", now.getEpochSecond());
    payload.put("exp", now.plusSeconds(validitySeconds).getEpochSecond());

    String encodedHeader = encodeJson(header);
    String encodedPayload = encodeJson(payload);
    String signingInput = encodedHeader + "." + encodedPayload;
    return signingInput + "." + sign(signingInput);
  }

  private JwtClaims parseClaims(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw new IllegalArgumentException("JWT 형식이 올바르지 않습니다.");
      }

      Map<String, Object> header = decodeJson(parts[0]);
      if (!JWT_ALGORITHM.equals(header.get("alg"))) {
        throw new IllegalArgumentException("JWT 알고리즘이 올바르지 않습니다.");
      }
      validateSignature(parts);

      Map<String, Object> payload = decodeJson(parts[1]);
      long expiresAt = readLongClaim(payload, "exp");
      if (Instant.now().getEpochSecond() >= expiresAt) {
        throw new JwtException(ErrorCode.EXPIRED_TOKEN, "JWT가 만료되었습니다.");
      }

      return new JwtClaims(
          Long.parseLong(readStringClaim(payload, "sub")),
          readStringClaim(payload, "email"),
          UserRole.valueOf(readStringClaim(payload, "role")),
          readStringClaim(payload, "tokenType"),
          readStringClaim(payload, "jti"),
          expiresAt);
    } catch (JwtException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new JwtException(ErrorCode.INVALID_TOKEN, e.getMessage());
    }
  }

  private String encodeJson(Map<String, Object> value) {
    try {
      return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (JacksonException exception) {
      throw new IllegalStateException("JWT JSON 직렬화에 실패했습니다.", exception);
    }
  }

  private Map<String, Object> decodeJson(String value) {
    try {
      return objectMapper.readValue(BASE64_URL_DECODER.decode(value), MAP_TYPE);
    } catch (IllegalArgumentException | JacksonException exception) {
      throw new IllegalArgumentException("JWT JSON 역직렬화에 실패했습니다.", exception);
    }
  }

  private String sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(secret, HMAC_SHA256));
      return BASE64_URL_ENCODER.encodeToString(
          mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("JWT 서명 생성에 실패했습니다.", exception);
    }
  }

  private void validateSignature(String[] parts) {
    String signingInput = parts[0] + "." + parts[1];
    String expectedSignature = sign(signingInput);
    if (!MessageDigest.isEqual(
        expectedSignature.getBytes(StandardCharsets.UTF_8),
        parts[2].getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("JWT 서명이 올바르지 않습니다.");
    }
  }

  private String readStringClaim(Map<String, Object> payload, String name) {
    Object value = payload.get(name);
    if (value instanceof String stringValue) {
      return stringValue;
    }
    throw new IllegalArgumentException("JWT claim이 올바르지 않습니다: " + name);
  }

  private long readLongClaim(Map<String, Object> payload, String name) {
    Object value = payload.get(name);
    if (value instanceof Number numberValue) {
      return numberValue.longValue();
    }
    throw new IllegalArgumentException("JWT claim이 올바르지 않습니다: " + name);
  }

  private void validateSecret(String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalArgumentException("JWT secret은 32자 이상이어야 합니다.");
    }
  }

  private void validateValiditySeconds(long validitySeconds, String tokenName) {
    if (validitySeconds <= 0) {
      throw new IllegalArgumentException(tokenName + " 만료 시간은 0보다 커야 합니다.");
    }
  }

  public record JwtRefreshClaims(Long userId, String jti, long expiresAtEpochSeconds) {}

  private record JwtClaims(
      Long userId,
      String email,
      UserRole role,
      String tokenType,
      String jti,
      long expiresAtEpochSeconds) {}
}
