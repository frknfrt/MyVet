package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.FormTemplateExtractionDraft;
import com.vetos.modules.ai.domain.FormTemplateExtractionPort;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.platform.http.OutboundHttp;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Dijital onay formlari (2026-11-07, Task 3): OllamaLabResultOcrAdapter ile AYNI desen (OLLAMA_BASE_URL
 * bossa modelConnected=false, "format":"json", vision modeli). Ham JSON metnini doner; ayristirma use case'te.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
@Slf4j
class OllamaFormTemplateExtractionAdapter implements FormTemplateExtractionPort {

    private final String baseUrl;
    private final String visionModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    OllamaFormTemplateExtractionAdapter(
        @Value("${ai.ollama.base-url:}") String baseUrl,
        @Value("${ai.ollama.vision-model:llava}") String visionModel,
        @Value("${ai.form-template.read-timeout-seconds:45}") int readTimeoutSeconds,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        if (readTimeoutSeconds < 1 || readTimeoutSeconds > 60) {
            throw new IllegalArgumentException("ai.form-template.read-timeout-seconds must be between 1 and 60");
        }
        this.baseUrl = baseUrl;
        this.visionModel = visionModel;
        this.objectMapper = objectMapper;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(readTimeoutSeconds));
    }

    @Override
    public FormTemplateExtractionDraft extract(OcrImageInput image) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            String raw = circuitBreaker.executeCallable(() -> outcomes.recordAiCall("form-template", "ollama", () -> extractViaOllama(image)));
            return new FormTemplateExtractionDraft(raw, true, visionModel);
        } catch (Exception e) {
            log.warn("Ollama form sablonu okuma basarisiz, bos taslaga duseluyor: {}", OcrFailureLogging.describe(e), e);
            return fallback();
        }
    }

    private String extractViaOllama(OcrImageInput image) throws Exception {
        Map<String, Object> body = Map.of(
            "model", visionModel,
            "system", FormTemplateExtractionPrompt.SYSTEM,
            "prompt", FormTemplateExtractionPrompt.INSTRUCTION,
            "images", List.of(Base64.getEncoder().encodeToString(image.content())),
            "stream", false,
            "format", "json",
            "options", OllamaOptions.wideContext()
        );
        String raw = restClient.post()
            .uri(baseUrl + "/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

        JsonNode envelope = objectMapper.readTree(raw);
        return ClaudeVisionCompletion.stripMarkdownFence(envelope.path("response").asText(""));
    }

    private FormTemplateExtractionDraft fallback() {
        return new FormTemplateExtractionDraft(null, false, visionModel);
    }
}
