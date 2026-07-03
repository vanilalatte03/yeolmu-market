package com.guingujig.yeolmumarket.domain.user.service;

import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNicknameQueryService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Map<Long, String> getNicknames(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }

    var distinctUserIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctUserIds.isEmpty()) {
      return Map.of();
    }

    return userRepository.findNicknamesByIds(distinctUserIds).stream()
        .collect(
            Collectors.toMap(
                UserRepository.UserNicknameProjection::getUserId,
                UserRepository.UserNicknameProjection::getNickname,
                (first, second) -> first));
  }
}
