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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lokal Ollama uzerinden tedavi onerisi uretimi -- OllamaSoapGenerationAdapter
 * ile ayni desen (OLLAMA_BASE_URL bos ise simule davranisa duser). SOAP'tan
 * farkli olarak format:json ISTEMIYOR -- tek bir serbest metin oneri yeterli,
 * S/O/A/P gibi alan ayristirmasi yok.
 *
 * ai.provider=claude ise bunun yerine ClaudeTreatmentRecommendationAdapter
 * aktif olur (bkz. o sinifin javadoc'u) -- Spring'in tek bir
 * TreatmentRecommendationPort bean'i olmasi gerektigi icin ikisi ayni anda
 * aktif olamaz.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
@Slf4j
class OllamaTreatmentRecommendationAdapter implements TreatmentRecommendationPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- gercek bir tedavi onerisi uretilemedi. Lutfen tedavi planini elle girin.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinik karar destegi asistanisin. Sana hekimin bir hasta icin yazdigi \
        degerlendirme (assessment) ve hastanin gecmis muayene ozetleri verilecek. Bu bilgilere \
        dayanarak olasi bir tedavi plani oner. Kesin tani koyma, sadece tedavi secenekleri sun. \
        Yanitin sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma.""";

    private final String baseUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    OllamaTreatmentRecommendationAdapter(
        @Value("${ai.ollama.base-url:}") String baseUrl,
        @Value("${ai.ollama.model:llama3.1}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @Override
    public TreatmentRecommendationDraft generate(TreatmentRecommendationInput input) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("treatment", "ollama", () -> generateViaOllama(input)));
        } catch (Exception e) {
            log.warn("Ollama tedavi onerisi uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private TreatmentRecommendationDraft generateViaOllama(TreatmentRecommendationInput input) throws Exception {
        String prompt = buildPrompt(input);
        Map<String, Object> body = Map.of(
            "model", model,
            "system", SYSTEM_PROMPT,
            "prompt", prompt,
            "stream", false
        );
        String raw = restClient.post()
            .uri(baseUrl + "/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

        JsonNode root = objectMapper.readTree(raw);
        String suggestion = root.path("response").asText("").trim();
        return new TreatmentRecommendationDraft(suggestion, true, model);
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
