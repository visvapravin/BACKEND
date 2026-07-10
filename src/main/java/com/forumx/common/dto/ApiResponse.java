package com.forumx.common.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    private String message;

    private T data;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String path;

    private String requestId;

    // ── Factory methods ─────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> failure(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> failure(String message, String requestId) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .requestId(requestId)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, T errorData, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(errorData)
                .path(path)
                .timestamp(Instant.now())
                .build();
    }
}
