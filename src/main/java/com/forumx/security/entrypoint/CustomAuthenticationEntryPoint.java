package com.forumx.security.entrypoint;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.common.dto.ApiResponse;
import com.forumx.common.exception.ErrorCode;
import com.forumx.common.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Custom authentication entry point that handles unauthenticated requests
 * by returning a structured JSON response instead of default HTML pages.
 */
@Slf4j
@Component("customAuthenticationEntryPoint")
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        if (response.isCommitted()) {
            log.debug("Response already committed. Skipping entrypoint commence.");
            return;
        }

        String path = request.getRequestURI();
        log.warn("Unauthorized access: path={}, reason={}", path, authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String requestId = request.getHeader("X-Request-ID");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.UNAUTHORIZED)
                .message("Authentication required")
                .path(path)
                .requestId(requestId)
                .timestamp(Instant.now())
                .build();

        ApiResponse<ErrorResponse> apiResponse = ApiResponse.error(
                "Unauthorized",
                errorResponse,
                path
        );
        apiResponse.setRequestId(requestId);

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
