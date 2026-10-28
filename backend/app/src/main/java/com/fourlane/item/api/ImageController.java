package com.fourlane.item.api;

import com.fourlane.global.error.ApiErrorResponse;
import com.fourlane.item.application.ItemQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Duration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ImageController {
    private final ItemQueryService queries;
    public ImageController(ItemQueryService queries) { this.queries = queries; }
    @GetMapping("/api/images/{storageKey}")
    @Operation(operationId = "getImage", summary = "출품 이미지")
    @ApiResponse(responseCode = "200", content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<InputStreamResource> image(@PathVariable String storageKey) {
        var image = queries.image(storageKey);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.sizeBytes()).header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new InputStreamResource(image.input()));
    }
}
