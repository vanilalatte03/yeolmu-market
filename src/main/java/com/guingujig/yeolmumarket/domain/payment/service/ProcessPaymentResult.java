package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.payment.dto.PaymentResponse;

public record ProcessPaymentResult(PaymentResponse response, boolean created) {}
