package com.fourlane.global.error;

import com.fourlane.global.error.ApiErrorResponse.LaneReference;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final LaneReference lane;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, LaneReference lane) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.lane = lane;
    }
}
