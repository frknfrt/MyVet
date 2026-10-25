package com.vetos.modules.ai.infrastructure.adapter;

import com.vetos.modules.ai.domain.PatientHistoryPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
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

/**
 * Anthropic Messages API uzerinden tani destegi uretimi -- ClaudeTreatmentRecommendationAdapter
 * ile birebir ayni desen (ayni port-yaklasimi, ayni "kimlik bilgisi yoksa simule et" davranisi,
 * duz metin yanit -- JSON parse gerekmedigi icin prefill/tool-use ihtiyaci yok). Fark: girdi
 * Assessment degil Subjective/Objective + vital/fizik muayene ozeti. ai.provider=claude oldugunda
 * aktif olur (bkz. OllamaDiagnosisSuggestionAdapter'daki @ConditionalOnProperty).
 *
 * ONEMLI: Bu adaptor kesin tani KOYMAZ, sadece olasi tani/ayirici tani listesi onerir --
 * SYSTEM_PROMPT bunu acikca belirtir. Sonuc her zaman "AI onerisi, hekim onayi gerekir"
 * ilkesiyle sunulur (bkz. CLAUDE.md "Yapma" bolumu, docs/design-system.md).
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@Slf4j
class ClaudeDiagnosisSuggestionAdapter implements DiagnosisSuggestionPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- gercek bir tani destegi uretilemedi. Lutfen Assessment alanini elle girin.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinik karar destegi asistanisin. Sana hastanin Subjective (sahip ifadesi/oyku), \
        Objective (fizik muayene bulgulari), varsa vital bulgular, fiziksel muayene ozeti ve gecmis muayene \
        kayitlari verilecek. Bu bilgilere dayanarak olasi tani(lar) veya ayirici tani listesi oner. \
        KESIN TANI KOYMA -- bu yalnizca hekimin degerlendirmesine yardimci bir on-oneridir, nihai karar \
        her zaman hekime aittir. Yanitin sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma."""
        + PatientHistoryPrompt.INSTRUCTION + PatientHistoryPrompt.CONCISE;

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    ClaudeDiagnosisSuggestionAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages", outcomes, circuitBreakers);
    }

    ClaudeDiagnosisSuggestionAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl, IntegrationOutcomes outcomes,
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
    public DiagnosisSuggestionDraft generate(DiagnosisSuggestionInput input) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("diagnosis", "claude", () -> generateViaClaude(input)));
        } catch (Exception e) {
            log.warn("Claude tani destegi uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private DiagnosisSuggestionDraft generateViaClaude(DiagnosisSuggestionInput input) throws Exception {
        String suggestion = ClaudeTextCompletion.complete(restClient, objectMapper, apiUrl, apiKey, model, SYSTEM_PROMPT, buildPrompt(input));
        return new DiagnosisSuggestionDraft(suggestion.trim(), true, model);
    }

    private String buildPrompt(DiagnosisSuggestionInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subjective: ").append(blankToNone(input.subjective())).append("\n");
        sb.append("Objective: ").append(blankToNone(input.objective())).append("\n");
        sb.append("Vital bulgular: ").append(blankToNone(input.vitalsSummary())).append("\n");
        sb.append("Fiziksel muayene ozeti: ").append(blankToNone(input.physicalExamSummary())).append("\n\n");
        sb.append(input.patientHistory() == null || input.patientHistory().isBlank() ? "Hasta gecmisi yok." : input.patientHistory());
        return sb.toString();
    }

    private String blankToNone(String s) {
        return (s == null || s.isBlank()) ? "belirtilmedi" : s;
    }

    private DiagnosisSuggestionDraft fallback() {
        return new DiagnosisSuggestionDraft(STATUS_NOTE, false, model);
    }
}
