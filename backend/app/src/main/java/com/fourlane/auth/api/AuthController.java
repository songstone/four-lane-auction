package com.fourlane.auth.api;

import com.fourlane.auth.application.*;
import com.fourlane.global.error.ApiErrorResponse;
import com.fourlane.member.api.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@ApiResponse(responseCode = "400", description = "INVALID_REQUEST", content = @Content(
        mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "UNAUTHENTICATED / INVALID_CREDENTIALS", content = @Content(
        mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
public class AuthController {
    private final AuthService auth;
    private final RefreshTokenCookie cookies;
    public AuthController(AuthService auth, RefreshTokenCookie cookies) { this.auth = auth; this.cookies = cookies; }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "signup", summary = "회원가입")
    @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = MemberResponse.class)))
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<MemberResponse> signup(@Valid @RequestBody AuthRequests.Signup request) {
        request.validate();
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(
                auth.signup(request.email(), request.password(), request.nickname())));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "login", summary = "로그인")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AuthResponses.Login.class)))
    public ResponseEntity<AuthResponses.Login> login(@Valid @RequestBody AuthRequests.Login request) {
        request.validate();
        return response(auth.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refresh", summary = "토큰 재발급")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AuthResponses.Login.class)))
    public ResponseEntity<AuthResponses.Login> refresh(@CookieValue(name = "refreshToken", required = false) String raw) {
        return response(auth.refresh(raw));
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "로그아웃")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> logout(@CookieValue(name = "refreshToken", required = false) String raw) {
        auth.logout(raw);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.delete()).build();
    }

    private ResponseEntity<AuthResponses.Login> response(AuthResult result) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                cookies.value(result.refreshToken(), result.refreshMaxAgeSeconds())).body(AuthResponses.Login.from(result));
    }
}
