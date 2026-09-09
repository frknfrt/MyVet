package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lokal Ollama uzerinden tani destegi uretimi -- OllamaTreatmentRecommendationAdapter
 * ile ayni desen (OLLAMA_BASE_URL bos ise simule davranisa duser). ai.provider=claude
 * ise bunun yerine ClaudeDiagnosisSuggestionAdapter aktif olur.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
@Slf4j
class OllamaDiagnosisSuggestionAdapter implements DiagnosisSuggestionPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- gercek bir tani destegi uretilemedi. Lutfen Assessment alanini elle girin.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinik karar destegi asistanisin. Sana hastanin Subjective (sahip ifadesi/oyku), \
        Objective (fizik muayene bulgulari), varsa vital bulgular, fiziksel muayene ozeti ve gecmis muayene \
        kayitlari verilecek. Bu bilgilere dayanarak olasi tani(lar) veya ayirici tani listesi oner. \
        KESIN TANI KOYMA -- bu yalnizca hekimin degerlendirmesine yardimci bir on-oneridir, nihai karar \
        her zaman hekime aittir. Yanitin sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma.""";

    private final String baseUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    OllamaDiagnosisSuggestionAdapter(
        @Value("${ai.ollama.base-url:}") String baseUrl,
        @Value("${ai.ollama.model:llama3.1}") String model,
        ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(120_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public DiagnosisSuggestionDraft generate(DiagnosisSuggestionInput input) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            return generateViaOllama(input);
        } catch (Exception e) {
            log.warn("Ollama tani destegi uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private DiagnosisSuggestionDraft generateViaOllama(DiagnosisSuggestionInput input) throws Exception {
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
        return new DiagnosisSuggestionDraft(suggestion, true, model);
    }

    private String buildPrompt(DiagnosisSuggestionInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subjective: ").append(blankToNone(input.subjective())).append("\n");
        sb.append("Objective: ").append(blankToNone(input.objective())).append("\n");
        sb.append("Vital bulgular: ").append(blankToNone(input.vitalsSummary())).append("\n");
        sb.append("Fiziksel muayene ozeti: ").append(blankToNone(input.physicalExamSummary())).append("\n\n");
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

    private String blankToNone(String s) {
        return (s == null || s.isBlank()) ? "belirtilmedi" : s;
    }

    private DiagnosisSuggestionDraft fallback() {
        return new DiagnosisSuggestionDraft(STATUS_NOTE, false, model);
    }
}
