package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private static final String JWT_ALGORITHM = "HS256";
  private static final String BEARER_TOKEN_TYPE = "Bearer";
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;
  private final byte[] secret;
  private final long accessTokenValiditySeconds;

  public JwtTokenProvider(
      ObjectMapper objectMapper,
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds) {
    this.objectMapper = objectMapper;
    validateSecret(secret);
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
  }

  public String issueAccessToken(User user) {
    return BEARER_TOKEN_TYPE + " " + generateAccessToken(user);
  }

  public String generateToken(User user) {
    return generateAccessToken(user);
  }

  public long getAccessTokenValiditySeconds() {
    return accessTokenValiditySeconds;
  }

  public Optional<Authentication> getAuthentication(String token) {
    try {
      JwtClaims claims = parseClaims(token);
      AuthenticatedUser principal =
          new AuthenticatedUser(claims.userId(), claims.email(), claims.role());
      return Optional.of(
          UsernamePasswordAuthenticationToken.authenticated(
              principal,
              token,
              List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private String generateAccessToken(User user) {
    Instant now = Instant.now();

    Map<String, Object> header = new LinkedHashMap<>();
    header.put("typ", "JWT");
    header.put("alg", JWT_ALGORITHM);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sub", user.getId().toString());
    payload.put("email", user.getEmail());
    payload.put("role", user.getRole().name());
    payload.put("iat", now.getEpochSecond());
    payload.put("exp", now.plusSeconds(accessTokenValiditySeconds).getEpochSecond());

    String encodedHeader = encodeJson(header);
    String encodedPayload = encodeJson(payload);
    String signingInput = encodedHeader + "." + encodedPayload;
    return signingInput + "." + sign(signingInput);
  }

  private JwtClaims parseClaims(String token) {
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
      throw new IllegalArgumentException("JWT가 만료되었습니다.");
    }

    return new JwtClaims(
        Long.parseLong(readStringClaim(payload, "sub")),
        readStringClaim(payload, "email"),
        UserRole.valueOf(readStringClaim(payload, "role")));
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

  private record JwtClaims(Long userId, String email, UserRole role) {}
}
