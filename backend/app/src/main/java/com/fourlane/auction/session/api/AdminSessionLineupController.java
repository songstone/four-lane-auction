package com.fourlane.auction.session.api;

import com.fourlane.auction.session.application.SessionLineupService;
import com.fourlane.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/admin/sessions", produces=MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="400",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="401",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="403",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="404",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="422",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="500",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
public class AdminSessionLineupController {
    private final SessionLineupService service;
    public AdminSessionLineupController(SessionLineupService service) { this.service = service; }
    @PutMapping(value="/{sessionId}/lanes/{laneNo}/lots", consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="replaceSessionLineup",summary="레인 전체 편성 교체")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=AdminSessionResponse.Lane.class)))
    public AdminSessionResponse.Lane replace(@PathVariable @Min(1) @Max(9007199254740991L) long sessionId,
            @PathVariable int laneNo, @RequestBody SessionLineupRequests.Replace request) {
        return AdminSessionResponse.lane(service.replace(sessionId, laneNo, request.ids()));
    }
}
