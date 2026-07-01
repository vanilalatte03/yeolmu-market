package com.guingujig.yeolmumarket.domain.user.service;

import com.guingujig.yeolmumarket.domain.review.service.ReviewRatingQueryService;
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
  private final ReviewRatingQueryService reviewRatingQueryService;

  /**
   * 회원 ID로 공개 프로필을 조회한다.
   *
   * <p>회원이 존재하지 않으면 {@code USER_NOT_FOUND}를 던진다.
   */
  @Transactional(readOnly = true)
  public GetUserResponse getUser(Long userId) {
    User user = getExistingUser(userId);
    return GetUserResponse.from(user, reviewRatingQueryService.getSummary(userId));
  }

  @Transactional(readOnly = true)
  public User getExistingUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public void validateUserExists(Long userId) {
    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
  }

  /**
   * 내 정보(닉네임, 비밀번호)를 수정한다.
   *
   * <p>두 필드 모두 null이면 {@code VALIDATION_FAILED}를 던진다. 회원이 존재하지 않으면 {@code USER_NOT_FOUND}를 던진다.
   * blank 값은 DTO 단에서 trim 후 {@code @Size} 검증으로 사전 차단된다.
   */
  @Transactional
  public UpdateUserResponse updateMe(Long userId, UpdateUserRequest request) {
    validateUpdateValue(request);

    User user = getExistingUser(userId);

    if (StringUtils.hasText(request.nickname())) {
      user.updateNickname(request.nickname());
    }
    if (StringUtils.hasText(request.password())) {
      // TODO: 비밀번호 변경 시 로그아웃 로직 추가
      user.updatePassword(passwordEncoder.encode(request.password()));
    }

    userRepository.flush();
    return UpdateUserResponse.from(user);
  }

  private void validateUpdateValue(UpdateUserRequest request) {
    if (!StringUtils.hasText(request.nickname()) && !StringUtils.hasText(request.password())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
  }
}
