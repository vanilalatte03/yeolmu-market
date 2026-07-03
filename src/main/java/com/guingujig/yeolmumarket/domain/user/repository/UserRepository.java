package com.guingujig.yeolmumarket.domain.user.repository;

import com.guingujig.yeolmumarket.domain.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  @Query(
      """
      select user.id as userId,
             user.nickname as nickname
      from User user
      where user.id in :userIds
      """)
  List<UserNicknameProjection> findNicknamesByIds(@Param("userIds") Collection<Long> userIds);

  interface UserNicknameProjection {
    Long getUserId();

    String getNickname();
  }
}
