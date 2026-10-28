package com.fourlane.global.error;

import com.fourlane.global.error.ApiErrorResponse.FieldViolation;
import com.fourlane.global.error.ApiErrorResponse.LaneReference;
import java.util.List;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final LaneReference lane;
    private final List<FieldViolation> fields;
    private String reviewStatus;
    private List<Long> itemIds;
    private List<ApiErrorResponse.ItemStage> items;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, LaneReference lane) {
        this(errorCode, lane, List.of());
    }

    private ApiException(ErrorCode errorCode, LaneReference lane, List<FieldViolation> fields) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.lane = lane;
        this.fields = List.copyOf(fields);
    }

    public static ApiException missingItems(List<Long> ids) {
        var e = new ApiException(ErrorCode.ITEM_NOT_FOUND); e.itemIds = List.copyOf(ids); return e;
    }
    public static ApiException unassignableItems(List<ApiErrorResponse.ItemStage> items) {
        var e = new ApiException(ErrorCode.ITEM_NOT_ASSIGNABLE); e.items = List.copyOf(items); return e;
    }
    public static ApiException invalidRequest(String field, String reason) {
        return new ApiException(ErrorCode.INVALID_REQUEST, null, List.of(new FieldViolation(field, reason)));
    }
    public static ApiException itemAlreadyReviewed(String status) {
        var exception = new ApiException(ErrorCode.ITEM_NOT_PENDING_REVIEW);
        exception.reviewStatus = status;
        return exception;
    }
}
