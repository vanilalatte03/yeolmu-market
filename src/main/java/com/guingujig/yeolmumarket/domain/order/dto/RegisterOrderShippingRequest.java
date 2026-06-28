package com.guingujig.yeolmumarket.domain.order.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterOrderShippingRequest(@NotBlank String trackingNumber) {}
