package com.fourlane.auction.session.api;

import com.fourlane.auction.session.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record SessionListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant serverTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Summary> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String nextCursor) {

    @Schema(name = "SessionSummary")
    public record Summary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SessionStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "4") int laneCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant scheduledStartAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"},
                    format = "date-time") Instant startedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"},
                    format = "date-time") Instant finishedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long lotCount) {}
}
