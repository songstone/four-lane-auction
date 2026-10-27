package com.fourlane.member.api;

import com.fourlane.global.error.ApiErrorResponse;
import com.fourlane.member.application.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {
    private final MemberService members;
    public MemberController(MemberService members) { this.members = members; }

    @GetMapping(value = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getMe", summary = "내 정보", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = MemberResponse.class)))
    @ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = ApiErrorResponse.class)))
    public MemberResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MemberResponse.from(members.get(Long.parseLong(jwt.getSubject())));
    }
}
