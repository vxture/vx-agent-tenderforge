// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.support;

public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message,
        String traceId
) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(true, data, null, "success", traceId);
    }

    public static ApiResponse<Void> failure(String errorCode, String message, String traceId) {
        return new ApiResponse<>(false, null, errorCode, message, traceId);
    }
}
