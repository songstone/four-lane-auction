package com.fourlane.item.api;

import com.fourlane.global.error.ApiErrorResponse;
import com.fourlane.item.application.ItemRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ItemController {
    private final ItemRegistrationService registration;
    public ItemController(ItemRegistrationService registration) { this.registration = registration; }
    @PostMapping(value = "/api/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "registerItem", summary = "출품 등록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = ItemResponses.Created.class)))
    @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ItemResponses.Created> register(@AuthenticationPrincipal Jwt jwt,
            @ModelAttribute ItemRequests.Registration request) {
        var item = registration.register(Long.parseLong(jwt.getSubject()), request.command(), request.uploads());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemResponses.Created.from(item));
    }
}
