package com.fourlane.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiErrorResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ErrorDetail error) {
    public static ApiErrorResponse of(ErrorCode code) {
        return of(code, null, List.of());
    }

    public static ApiErrorResponse of(ErrorCode code, LaneReference lane, List<FieldViolation> fields) {
        return of(code, lane, fields, null);
    }
    public static ApiErrorResponse of(ErrorCode code, LaneReference lane, List<FieldViolation> fields, String reviewStatus) {
        return new ApiErrorResponse(new ErrorDetail(
                code.name(), code.getMessage(), lane,
                code.getStatus().value() == 400 ? List.copyOf(fields) : null, reviewStatus, null, null));
    }

    public static ApiErrorResponse of(ApiException e) {
        var code = e.getErrorCode();
        return new ApiErrorResponse(new ErrorDetail(code.name(), code.getMessage(), e.getLane(),
            code.getStatus().value() == 400 ? e.getFields() : null, e.getReviewStatus(), e.getItemIds(), e.getItems()));
    }
    @Schema(requiredProperties={"itemId","stage"})
    public record ItemStage(long itemId, @Schema(allowableValues={"PENDING_REVIEW","REJECTED","APPROVED","SCHEDULED","ON_AUCTION","SOLD","UNSOLD"}) String stage) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            LaneReference lane, List<FieldViolation> fields,
            @Schema(allowableValues = {"PENDING_REVIEW", "APPROVED", "REJECTED"}) String reviewStatus, List<Long> itemIds, List<ItemStage> items) {}

    public record LaneReference(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String epoch,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long seq) {}

    public record FieldViolation(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}
}
