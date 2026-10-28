package com.fourlane.item.api;

import io.swagger.v3.oas.annotations.media.Schema;

public final class AdminItemRequests {
    private AdminItemRequests() {}
    @Schema(name = "ItemRejectionRequest")
    public record Rejection(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500,
            description = "원문 코드 포인트 기준, 공백만 거절, 원문 보존") String reason) {}
}
