package com.guingujig.yeolmumarket.domain.refund.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRefundRequestRequest(@NotBlank String reason) {}
