package com.guingujig.yeolmumarket.domain.refund.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRefundRequest(@NotBlank String reason) {}
