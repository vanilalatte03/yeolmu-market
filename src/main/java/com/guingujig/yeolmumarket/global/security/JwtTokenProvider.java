package com.guingujig.yeolmumarket.global.security;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtTokenProvider {

  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final String JWT_ALGORITHM = "HS256";
  private static final String BEARER_TOKEN_TYPE = "Bearer";
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

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

  private String encodeJson(Map<String, Object> value) {
    try {
      return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (JacksonException exception) {
      throw new IllegalStateException("JWT JSON 직렬화에 실패했습니다.", exception);
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

  private void validateSecret(String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalArgumentException("JWT secret은 32자 이상이어야 합니다.");
    }
  }
}
