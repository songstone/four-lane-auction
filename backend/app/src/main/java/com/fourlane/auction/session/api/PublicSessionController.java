package com.fourlane.auction.session.api;

import com.fourlane.auction.session.application.PublicSessionQueryService;
import com.fourlane.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiResponse(responseCode="400", content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="401", content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="404", content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="500", content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class)))
public class PublicSessionController {
    private final PublicSessionQueryService queries;
    public PublicSessionController(PublicSessionQueryService queries) { this.queries = queries; }

    @GetMapping(value="/api/sessions/{sessionId}", produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="getPublicSession", summary="예정 회차 관전 상세")
    @ApiResponse(responseCode="200",content=@Content(mediaType="application/json",schema=@Schema(implementation=PublicSessionResponses.Detail.class)))
    public PublicSessionResponses.Detail session(@PathVariable @Min(1) long sessionId, @AuthenticationPrincipal Jwt jwt) {
        return PublicSessionResponses.from(queries.session(sessionId, jwt == null ? null : Long.valueOf(jwt.getSubject())));
    }

    @GetMapping(value="/api/lots/{lotId}", produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="getPublicLot", summary="공개 Lot 상세")
    @ApiResponse(responseCode="200",content=@Content(mediaType="application/json",schema=@Schema(implementation=PublicSessionResponses.LotDetail.class)))
    public PublicSessionResponses.LotDetail lot(@PathVariable @Min(1) long lotId) {
        return PublicSessionResponses.from(queries.lot(lotId));
    }
}
