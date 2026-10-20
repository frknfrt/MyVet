package com.vetos.platform.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vetos.platform.web.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Filtrelerden (Spring MVC disinda) yazilan 429 yaniti -- govde, GlobalExceptionHandler'in
 * urettigi ErrorResponse ile ayni bicimde (requestId dahil; RequestIdFilter once calisir).
 */
public final class RateLimitResponses {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private RateLimitResponses() {
    }

    public static String message(long retryAfterSeconds) {
        return "Çok fazla istek gönderildi, lütfen " + retryAfterSeconds + " saniye sonra tekrar deneyin.";
    }

    public static void write(HttpServletResponse response, long retryAfterSeconds, String path) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(
            ErrorResponse.of("RATE_LIMITED", message(retryAfterSeconds), path)
        ));
    }
}
