package com.fourlane.auction.policy.api;

import com.fourlane.auction.policy.application.BidPolicyService;
import com.fourlane.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/admin/bid-policies", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminBidPolicyController {
    private final BidPolicyService policies;
    public AdminBidPolicyController(BidPolicyService policies) { this.policies = policies; }
    @GetMapping
    @Operation(operationId = "listBidPolicies", summary = "호가 정책 버전 목록")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BidPolicyResponses.Policies.class)))
    public BidPolicyResponses.Policies list() {
        return new BidPolicyResponses.Policies(policies.list().stream().map(BidPolicyResponses.Policy::from).toList());
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBidPolicy", summary = "새 호가 정책 버전 등록")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = BidPolicyResponses.Policy.class)))
    public BidPolicyResponses.Policy create(@AuthenticationPrincipal Jwt jwt, @RequestBody BidPolicyRequests.Create request) {
        if (request == null) throw com.fourlane.global.error.ApiException.invalidRequest("tiers", "REQUIRED");
        return BidPolicyResponses.Policy.from(policies.create(Long.parseLong(jwt.getSubject()), request.input()));
    }
}
