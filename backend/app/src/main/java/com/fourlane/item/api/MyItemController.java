package com.fourlane.item.api;

import com.fourlane.global.error.ApiErrorResponse;
import com.fourlane.item.application.ItemQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class MyItemController {
    private final ItemQueryService queries;
    public MyItemController(ItemQueryService queries) { this.queries = queries; }
    @GetMapping(value = "/api/me/items", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listMyItems", summary = "내 출품 목록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ItemResponses.Page.class)))
    @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ItemResponses.Page list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String cursor) {
        return ItemResponses.Page.from(queries.list(Long.parseLong(jwt.getSubject()), size, cursor));
    }
}
