package com.fourlane.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiErrorResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ErrorDetail error) {
    public static ApiErrorResponse of(ErrorCode code) {
        return of(code, null, List.of());
    }

    public static ApiErrorResponse of(ErrorCode code, LaneReference lane, List<FieldViolation> fields) {
        return new ApiErrorResponse(new ErrorDetail(
                code.name(), code.getMessage(), lane,
                code.getStatus().value() == 400 ? List.copyOf(fields) : null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            LaneReference lane, List<FieldViolation> fields) {}

    public record LaneReference(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String epoch,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long seq) {}

    public record FieldViolation(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}
}
