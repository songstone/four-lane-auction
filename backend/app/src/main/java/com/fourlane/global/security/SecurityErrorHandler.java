package com.fourlane.global.security;

import com.fourlane.global.error.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;
    public SecurityErrorHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, ErrorCode.UNAUTHENTICATED);
    }
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
            throws IOException { write(response, ErrorCode.FORBIDDEN); }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code));
    }
}
