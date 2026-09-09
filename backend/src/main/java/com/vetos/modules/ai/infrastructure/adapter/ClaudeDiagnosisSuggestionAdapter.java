package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        her zaman hekime aittir. Yanitin sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma.""";

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;

    @Autowired
    ClaudeDiagnosisSuggestionAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages");
    }

    ClaudeDiagnosisSuggestionAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(120_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public DiagnosisSuggestionDraft generate(DiagnosisSuggestionInput input) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            return generateViaClaude(input);
        } catch (Exception e) {
            log.warn("Claude tani destegi uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private DiagnosisSuggestionDraft generateViaClaude(DiagnosisSuggestionInput input) throws Exception {
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
        return new DiagnosisSuggestionDraft(suggestion, true, model);
    }

    /**
     * content[] her zaman ilk elemanda metin bloğu ICERMEZ -- extended thinking
     * acik oldugunda model once bir "thinking" blogu (type=thinking, "text" alani
     * yok), ardindan asil "text" blogunu dondurur. Bu yuzden content[0]'i sabit
     * varsaymak yerine ilk type=="text" blogunu ariyoruz (2026-09-09, ayni bug
     * ClaudeTreatmentRecommendationAdapter'da bulundu -- bkz. implementation-plan.md).
     */
    private String extractText(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText("").trim();
            }
        }
        return "";
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
