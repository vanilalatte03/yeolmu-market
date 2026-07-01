package com.guingujig.yeolmumarket.domain.payment.service;

import com.guingujig.yeolmumarket.domain.payment.dto.CreatePaymentRequest;

public record ProcessPaymentCommand(
    Long buyerId, Long orderId, String idempotencyKey, CreatePaymentRequest request) {}
