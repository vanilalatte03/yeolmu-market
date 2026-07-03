package com.guingujig.yeolmumarket.domain.product.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductHiddenStatusRequest(
    @NotNull(message = "숨김 여부는 필수입니다.") Boolean hidden) {}
