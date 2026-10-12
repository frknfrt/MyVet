package com.vetos.platform.web;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

/**
 * requestId, RequestIdFilter'in MDC'ye koydugu id'dir -- kullanicinin
 * gordugu hatayi sunucu loguyla eslestirmek icin. Filtre disinda (orn.
 * zamanlanmis is) uretilirse null.
 */
public record ErrorResponse(
    String errorCode,
    String message,
    List<FieldErrorItem> fieldErrors,
    Instant timestamp,
    String path,
    String requestId
) {

    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(errorCode, message, null, Instant.now(), path, MDC.get("requestId"));
    }

    public static ErrorResponse ofValidation(String message, List<FieldErrorItem> fieldErrors, String path) {
        return new ErrorResponse("VALIDATION_FAILED", message, fieldErrors, Instant.now(), path, MDC.get("requestId"));
    }

    public record FieldErrorItem(String field, String message) {}
}
