package com.fourlane.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Code and status catalog from docs/API.md section 9; no domain behavior here. */
@Getter
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    INVALID_BID_AMOUNT(HttpStatus.BAD_REQUEST, "입찰 금액을 확인해 주세요."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    BIDDER_SUSPENDED(HttpStatus.FORBIDDEN, "입찰이 정지된 회원입니다."),
    SELF_BID_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 출품물에는 입찰할 수 없습니다."),
    NOT_TRADE_BUYER(HttpStatus.FORBIDDEN, "낙찰자만 결제할 수 있습니다."),
    NOT_ITEM_SELLER(HttpStatus.FORBIDDEN, "본인 출품물만 재출품할 수 있습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다."),
    LANE_NOT_FOUND(HttpStatus.NOT_FOUND, "레인을 찾을 수 없습니다."),
    LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Lot을 찾을 수 없습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "출품물을 찾을 수 없습니다."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    LOT_MISMATCH(HttpStatus.CONFLICT, "현재 진행 중인 Lot이 아닙니다."),
    PRICE_CHANGED(HttpStatus.CONFLICT, "현재가가 바뀌었습니다."),
    ALREADY_HIGHEST_BIDDER(HttpStatus.CONFLICT, "이미 최고 입찰자입니다."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "이미지 용량이 너무 큽니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "다른 요청에 사용된 멱등 키입니다."),
    LANE_NOT_RUNNING(HttpStatus.UNPROCESSABLE_CONTENT, "진행 중인 레인이 아닙니다."),
    LANE_NOT_PAUSED(HttpStatus.UNPROCESSABLE_CONTENT, "정지 중인 레인이 아닙니다."),
    LOT_NOT_BIDDING(HttpStatus.UNPROCESSABLE_CONTENT, "입찰 가능한 Lot이 아닙니다."),
    TRADE_NOT_AWAITING_PAYMENT(HttpStatus.UNPROCESSABLE_CONTENT, "결제 대기 상태가 아닙니다."),
    PAYMENT_DEADLINE_PASSED(HttpStatus.UNPROCESSABLE_CONTENT, "결제 기한이 지났습니다."),
    RELIST_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "재출품할 수 없는 상태입니다."),
    ITEM_NOT_PENDING_REVIEW(HttpStatus.UNPROCESSABLE_CONTENT, "검수 대기 상태가 아닙니다."),
    ITEM_NOT_ASSIGNABLE(HttpStatus.UNPROCESSABLE_CONTENT, "편성할 수 없는 출품물입니다."),
    SESSION_NOT_SCHEDULED(HttpStatus.UNPROCESSABLE_CONTENT, "예정 상태의 회차가 아닙니다."),
    SCHEDULED_START_IN_PAST(HttpStatus.UNPROCESSABLE_CONTENT, "예정 시각은 미래여야 합니다."),
    SESSION_NOT_RUNNING(HttpStatus.UNPROCESSABLE_CONTENT, "진행 중인 회차가 아닙니다."),
    SESSION_START_CONDITION_NOT_MET(HttpStatus.UNPROCESSABLE_CONTENT, "회차 시작 조건이 충족되지 않았습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    BID_RESULT_UNKNOWN(HttpStatus.INTERNAL_SERVER_ERROR, "입찰 결과를 확인할 수 없습니다."),
    LANE_OVERLOADED(HttpStatus.SERVICE_UNAVAILABLE, "레인이 혼잡합니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
