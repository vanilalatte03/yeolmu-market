package com.guingujig.yeolmumarket.domain.payment.service;

public record CancelPaymentCommand(Long buyerId, Long paymentId, String reason) {}
