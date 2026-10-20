package com.vetos.platform.observability;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * LOG_FORMAT=ecs (JSON log) iken 2. katman guvenlik agi: "message" alanindaki 11 haneli
 * sayilari maskeler (spec S4.5). Duz metin modunda ayni regex logback %replace ile uygulanir
 * (application.yml logging.pattern.console).
 */
public class PiiMaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(
            JsonWriter.ValueProcessor.of(String.class, LogMasking::elevenDigits).whenHasPath("message"));
    }
}
