package com.fourlane.auction.session.api;

import com.fourlane.auction.session.application.SessionQueryService;
import com.fourlane.auction.session.domain.SessionStatus;
import com.fourlane.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    private final SessionQueryService queries;

    public SessionController(SessionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/sessions")
    @Operation(operationId = "listSessions", summary = "상태별 회차 목록")
    @ApiResponse(responseCode = "200", description = "회차 목록",
            content = @Content(schema = @Schema(implementation = SessionListResponse.class)))
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SessionListResponse list(
            @RequestParam SessionStatus status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String cursor) {
        var result = queries.list(status, size, cursor);
        return new SessionListResponse(result.serverTime(), result.items().stream()
                .map(row -> new SessionListResponse.Summary(row.getSessionId(), row.getStatus(),
                        row.getLaneCount(), row.getScheduledStartAt(), row.getStartedAt(),
                        row.getFinishedAt(), row.getLotCount()))
                .toList(), result.nextCursor());
    }
}
