package com.guingujig.yeolmumarket.domain.user.service;

import com.guingujig.yeolmumarket.domain.user.dto.GetUserResponse;
import com.guingujig.yeolmumarket.domain.user.dto.UpdateUserRequest;
import com.guingujig.yeolmumarket.domain.user.dto.UpdateUserResponse;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import com.guingujig.yeolmumarket.global.exception.BusinessException;
import com.guingujig.yeolmumarket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

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

  @Transactional
  public UpdateUserResponse updateMe(Long userId, UpdateUserRequest request) {
    if (!StringUtils.hasText(request.nickname()) && !StringUtils.hasText(request.password())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    if (StringUtils.hasText(request.nickname())) {
      user.updateNickname(request.nickname());
    }
    if (StringUtils.hasText(request.password())) {
      user.updatePassword(passwordEncoder.encode(request.password()));
    }

    userRepository.flush();
    return UpdateUserResponse.from(user);
  }
}
