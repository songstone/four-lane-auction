package com.fourlane.global.error;

import com.fourlane.global.error.ApiErrorResponse.FieldViolation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code, exception.getLane(), List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Normalize MVC errors to statuses already defined by API.md section 9.
        ErrorCode code = switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 413 -> ErrorCode.IMAGE_TOO_LARGE;
            default -> status.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
        List<FieldViolation> fields = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldViolation(error.getField(), reason(error.getCode())))
                        .toList()
                : List.of();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(ApiErrorResponse.of(code, null, fields), responseHeaders, code.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        LOG.error("Unhandled request failure", exception);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    private static String reason(String constraint) {
        if (constraint == null) {
            return "INVALID_FORMAT";
        }
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero" -> "OUT_OF_RANGE";
            default -> "INVALID_FORMAT";
        };
    }
}
