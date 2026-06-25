package com.guingujig.yeolmumarket.domain.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(min = 1, max = 30, message = "닉네임은 1자 이상 30자 이하여야 합니다.") String nickname,
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.") String password) {}
