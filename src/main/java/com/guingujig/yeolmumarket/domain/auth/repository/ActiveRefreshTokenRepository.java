package com.guingujig.yeolmumarket.domain.auth.repository;

import java.time.Duration;

public interface ActiveRefreshTokenRepository {

  void save(Long userId, String refreshJti, Duration ttl);

  boolean rotate(Long userId, String currentRefreshJti, String newRefreshJti, Duration ttl);

  void deleteByUserId(Long userId);
}
