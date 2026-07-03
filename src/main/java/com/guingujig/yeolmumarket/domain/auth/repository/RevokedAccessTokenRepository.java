package com.guingujig.yeolmumarket.domain.auth.repository;

import java.time.Duration;

public interface RevokedAccessTokenRepository {

  void add(String tokenHash, Duration ttl);

  boolean exists(String tokenHash);
}
