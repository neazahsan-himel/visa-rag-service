package com.neaz.travel.visa.common.model;

import java.time.Instant;

/**
 * Generic response envelope returned by every REST endpoint, giving clients
 * a consistent shape for both successful and error responses.
 *
 * @param success   whether the request succeeded
 * @param data      the response payload; {@code null} on error
 * @param message   a human-readable message; {@code null} on success unless explicitly set
 * @param timestamp when this response was constructed
 * @param <T>       the payload type
 */
public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}