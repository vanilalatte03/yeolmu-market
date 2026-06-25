package com.guingujig.yeolmumarket.domain.user.service;

import com.guingujig.yeolmumarket.domain.user.dto.GetUserResponse;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  /**
   * 회원 ID로 공개 프로필을 조회한다.
   *
   * <p>회원이 존재하지 않으면 {@code USER_NOT_FOUND}를 던진다.
   */
  @Transactional(readOnly = true)
  public GetUserResponse getUser(Long userId) {
    return userRepository
        .findById(userId)
        .map(GetUserResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }
}
