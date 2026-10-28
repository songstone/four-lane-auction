package com.fourlane.auction.session.api;
import com.fourlane.auction.session.application.*;
import com.fourlane.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(value="/api/admin/sessions",produces=MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="400",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="401",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="403",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="404",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="422",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="500",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
public class AdminSessionController {
    private final AdminSessionService commands;private final AdminSessionQueryService queries;
    public AdminSessionController(AdminSessionService commands,AdminSessionQueryService queries){this.commands=commands;this.queries=queries;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId="createSession",summary="회차 생성")
    @ApiResponse(responseCode="201",content=@Content(schema=@Schema(implementation=AdminSessionResponse.class)))
    public AdminSessionResponse create(@RequestBody SessionRequests.Create request){return AdminSessionResponse.from(commands.create(request.input()));}
    @GetMapping("/{sessionId}") @Operation(operationId="getAdminSession",summary="운영자 회차 상세")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=AdminSessionResponse.class)))
    public AdminSessionResponse get(@PathVariable @Min(1) @Max(9007199254740991L) long sessionId){return AdminSessionResponse.from(queries.get(sessionId));}
    @PatchMapping(value="/{sessionId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="patchSession",summary="예정 시각과 시간 파라미터 변경")
    @ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=AdminSessionResponse.class)))
    public AdminSessionResponse patch(@PathVariable @Min(1) @Max(9007199254740991L) long sessionId,@RequestBody SessionRequests.Patch request){
        return AdminSessionResponse.from(commands.patch(sessionId,request.input()));
    }
}
