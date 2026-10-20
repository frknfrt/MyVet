package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.TreatmentRecommendationDraft;
import com.vetos.modules.ai.domain.TreatmentRecommendationInput;
import com.vetos.modules.ai.domain.TreatmentRecommendationPort;
import com.vetos.platform.http.OutboundHttp;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Anthropic Messages API uzerinden tedavi onerisi uretimi --
 * OllamaTreatmentRecommendationAdapter ile ayni sozlesme (TreatmentRecommendationPort)
 * ve ayni "kimlik bilgisi yoksa simule et" deseni. ai.provider=claude oldugunda
 * aktif olur (bkz. OllamaTreatmentRecommendationAdapter'daki @ConditionalOnProperty).
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@Slf4j
class ClaudeTreatmentRecommendationAdapter implements TreatmentRecommendationPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- gercek bir tedavi onerisi uretilemedi. Lutfen tedavi planini elle girin.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinik karar destegi asistanisin. Sana hekimin bir hasta icin yazdigi \
        degerlendirme (assessment) ve hastanin gecmis muayene ozetleri verilecek. Bu bilgilere \
        dayanarak olasi bir tedavi plani oner. Kesin tani koyma, sadece tedavi secenekleri sun. \
        Yanitin sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma.""";

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    ClaudeTreatmentRecommendationAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages", outcomes, circuitBreakers);
    }

    ClaudeTreatmentRecommendationAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl, IntegrationOutcomes outcomes,
                                  CircuitBreakerRegistry circuitBreakers) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @Override
    public TreatmentRecommendationDraft generate(TreatmentRecommendationInput input) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("treatment", "claude", () -> generateViaClaude(input)));
        } catch (Exception e) {
            log.warn("Claude tedavi onerisi uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private TreatmentRecommendationDraft generateViaClaude(TreatmentRecommendationInput input) throws Exception {
        String prompt = buildPrompt(input);
        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 1024,
            "system", SYSTEM_PROMPT,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        String raw = restClient.post()
            .uri(apiUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

        JsonNode root = objectMapper.readTree(raw);
        String suggestion = extractText(root);
        return new TreatmentRecommendationDraft(suggestion, true, model);
    }

    /**
     * content[] her zaman ilk elemanda metin bloğu ICERMEZ -- extended thinking
     * acik oldugunda model once bir "thinking" blogu (type=thinking, "text" alani
     * yok), ardindan asil "text" blogunu dondurur. Bu yuzden content[0]'i sabit
     * varsaymak yerine ilk type=="text" blogunu ariyoruz (2026-09-09, bos oneri
     * bug'i: claude-sonnet-5 + extended thinking -- bkz. implementation-plan.md).
     */
    private String extractText(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText("").trim();
            }
        }
        return "";
    }

    private String buildPrompt(TreatmentRecommendationInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Guncel degerlendirme: ").append(input.currentAssessment()).append("\n\n");
        if (input.history().isEmpty()) {
            sb.append("Gecmis muayene kaydi yok.");
        } else {
            sb.append("Gecmis muayeneler (en yeniden eskiye):\n");
            sb.append(input.history().stream()
                .map(h -> "- " + h.date() + " | Degerlendirme: " + h.assessment() + " | Plan: " + h.plan())
                .collect(Collectors.joining("\n")));
        }
        return sb.toString();
    }

    private TreatmentRecommendationDraft fallback() {
        return new TreatmentRecommendationDraft(STATUS_NOTE, false, model);
    }
}
