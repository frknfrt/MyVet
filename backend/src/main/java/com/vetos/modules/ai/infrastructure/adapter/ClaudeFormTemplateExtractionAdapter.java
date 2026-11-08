package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.FormTemplateExtractionDraft;
import com.vetos.modules.ai.domain.FormTemplateExtractionPort;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.platform.http.OutboundHttp;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Dijital onay formlari (2026-11-07, Task 3): ClaudeLabResultOcrAdapter ile AYNI desen (vision, devre
 * kesici, hata -> modelConnected=false). Fark: JSON'u ayristirmaz, kod blogu temizlenmis ham metni doner
 * (ayristirma/dogrulama/yeniden deneme use case'te). Uzun formlar icin yuksek token siniri; bu akisa ozel yapilandirilabilir okuma suresi.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@Slf4j
class ClaudeFormTemplateExtractionAdapter implements FormTemplateExtractionPort {

    private static final int MAX_TOKENS = 8192;

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    ClaudeFormTemplateExtractionAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers,
        @Value("${ai.form-template.read-timeout-seconds:45}") int readTimeoutSeconds
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages", outcomes, circuitBreakers, readTimeoutSeconds);
    }

    ClaudeFormTemplateExtractionAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl,
                                        IntegrationOutcomes outcomes, CircuitBreakerRegistry circuitBreakers, int readTimeoutSeconds) {
        if (readTimeoutSeconds < 1 || readTimeoutSeconds > 60) {
            throw new IllegalArgumentException("ai.form-template.read-timeout-seconds must be between 1 and 60");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(readTimeoutSeconds));
    }

    @Override
    public FormTemplateExtractionDraft extract(OcrImageInput image) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            String raw = circuitBreaker.executeCallable(() -> outcomes.recordAiCall("form-template", "claude",
                () -> ClaudeVisionCompletion.completeText(restClient, objectMapper, apiUrl, apiKey, model,
                    FormTemplateExtractionPrompt.SYSTEM, List.of(image), FormTemplateExtractionPrompt.INSTRUCTION, MAX_TOKENS)));
            return new FormTemplateExtractionDraft(raw, true, model);
        } catch (Exception e) {
            log.warn("Claude form sablonu okuma basarisiz, bos taslaga duseluyor: {}", OcrFailureLogging.describe(e), e);
            return fallback();
        }
    }

    private FormTemplateExtractionDraft fallback() {
        return new FormTemplateExtractionDraft(null, false, model);
    }
}
