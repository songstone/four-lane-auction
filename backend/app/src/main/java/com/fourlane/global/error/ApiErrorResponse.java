package com.fourlane.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ApiErrorResponse(ErrorDetail error) {
    public static ApiErrorResponse of(ErrorCode code) {
        return of(code, null, List.of());
    }

    public static ApiErrorResponse of(ErrorCode code, LaneReference lane, List<FieldViolation> fields) {
        return new ApiErrorResponse(new ErrorDetail(
                code.name(), code.getMessage(), lane,
                code.getStatus().value() == 400 ? List.copyOf(fields) : null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(String code, String message, LaneReference lane, List<FieldViolation> fields) {}

    public record LaneReference(String epoch, long seq) {}

    public record FieldViolation(String field, String reason) {}
}
