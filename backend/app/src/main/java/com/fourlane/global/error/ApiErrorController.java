package com.fourlane.global.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Keeps servlet error dispatches on the same contract as MVC exceptions. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ApiErrorResponse> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer value ? value : 500;
        ErrorCode code = switch (status) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 413 -> ErrorCode.IMAGE_TOO_LARGE;
            default -> status >= 400 && status < 500 ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
        return ResponseEntity.status(code.getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code));
    }
}
