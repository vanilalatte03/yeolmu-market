package com.guingujig.yeolmumarket.domain.payment.dto;

import com.guingujig.yeolmumarket.domain.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(@NotNull PaymentMethod method, MockPaymentResult result) {}
