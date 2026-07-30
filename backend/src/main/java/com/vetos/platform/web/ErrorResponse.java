package com.vetos.platform.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String errorCode,
    String message,
    List<FieldErrorItem> fieldErrors,
    Instant timestamp,
    String path
) {

    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(errorCode, message, null, Instant.now(), path);
    }

    public static ErrorResponse ofValidation(String message, List<FieldErrorItem> fieldErrors, String path) {
        return new ErrorResponse("VALIDATION_FAILED", message, fieldErrors, Instant.now(), path);
    }

    public record FieldErrorItem(String field, String message) {}
}
