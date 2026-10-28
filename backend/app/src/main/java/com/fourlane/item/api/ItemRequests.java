package com.fourlane.item.api;

import com.fourlane.global.error.ApiException;
import com.fourlane.item.application.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public final class ItemRequests {
    private ItemRequests() {}
    @Schema(name = "ItemRegistrationRequest")
    public record Registration(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100) String name,
            @Schema(maxLength = 2000) String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1000~1000000000, 1000원 단위 텍스트 정수") String startPrice,
            @Schema(description = "시작가 이상 1000000000 이하, 1000원 단위 텍스트 정수. 빈 값은 시작가") String reservePrice,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1~10장, 장당 10000000바이트, JPEG/PNG/WebP") List<MultipartFile> images) {
        public ItemRegistrationService.Command command() {
            long start = parse(startPrice, "startPrice");
            long reserve = reservePrice == null || reservePrice.isBlank() ? start : parse(reservePrice, "reservePrice");
            return new ItemRegistrationService.Command(name, description, start, reserve);
        }
        public List<ImageUpload> uploads() {
            return images == null ? List.of() : images.stream().map(file -> new ImageUpload(file.getSize(), file::getInputStream)).toList();
        }
    }
    private static long parse(String value, String field) {
        if (value == null || value.isBlank()) throw ApiException.invalidRequest(field, "REQUIRED");
        if (!value.matches("-?[0-9]+")) throw ApiException.invalidRequest(field, "INVALID_FORMAT");
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw ApiException.invalidRequest(field, "OUT_OF_RANGE"); }
    }
}
