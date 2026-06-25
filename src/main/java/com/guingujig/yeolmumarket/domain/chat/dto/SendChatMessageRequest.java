package com.guingujig.yeolmumarket.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record SendChatMessageRequest(@NotBlank String content) {}
