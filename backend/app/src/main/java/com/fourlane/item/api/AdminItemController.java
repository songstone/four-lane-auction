package com.fourlane.item.api;

import com.fourlane.global.error.ApiErrorResponse;
import com.fourlane.item.application.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/admin/items", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminItemController {
    private final AdminItemQueryService queries;
    private final ItemReviewService reviews;
    public AdminItemController(AdminItemQueryService queries, ItemReviewService reviews) { this.queries = queries; this.reviews = reviews; }
    @GetMapping
    @Operation(operationId = "listAdminItems", summary = "운영자 출품 목록")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminItemResponses.Page.class)))
    public AdminItemResponses.Page list(@RequestParam ItemResponses.Stage stage,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String cursor) {
        return AdminItemResponses.Page.from(queries.list(stage.name(), size, cursor));
    }
    @GetMapping("/{itemId}")
    @Operation(operationId = "getAdminItem", summary = "운영자 출품 상세 (최저 낙찰가 포함)")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminItemResponses.Detail.class)))
    public AdminItemResponses.Detail detail(@PathVariable @Positive long itemId) { return AdminItemResponses.Detail.from(queries.detail(itemId)); }
    @PostMapping("/{itemId}/approve")
    @Operation(operationId = "approveItem", summary = "출품 승인")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminItemResponses.Reviewed.class)))
    public AdminItemResponses.Reviewed approve(@PathVariable @Positive long itemId) { return AdminItemResponses.Reviewed.from(reviews.approve(itemId)); }
    @PostMapping(value = "/{itemId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "rejectItem", summary = "출품 반려")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AdminItemResponses.Reviewed.class)))
    public AdminItemResponses.Reviewed reject(@PathVariable @Positive long itemId, @RequestBody AdminItemRequests.Rejection request) {
        return AdminItemResponses.Reviewed.from(reviews.reject(itemId, request.reason()));
    }
}
