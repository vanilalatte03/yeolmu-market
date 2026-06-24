package com.guingujig.yeolmumarket.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;

public interface ActiveRefreshTokenRepository {

  void save(Long userId, String tokenHash, Duration ttl);

  Optional<String> findHashByUserId(Long userId);

  void deleteByUserId(Long userId);
}
