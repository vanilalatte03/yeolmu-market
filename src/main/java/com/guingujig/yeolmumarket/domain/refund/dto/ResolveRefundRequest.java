package com.guingujig.yeolmumarket.domain.refund.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveRefundRequest(@NotNull RefundResolution resolution, String reason) {}
