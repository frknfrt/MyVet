package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.TreatmentRecommendationDraft;
import com.vetos.modules.ai.domain.TreatmentRecommendationInput;
import com.vetos.modules.ai.domain.TreatmentRecommendationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lokal Ollama uzerinden tedavi onerisi uretimi -- OllamaSoapGenerationAdapter
 * ile ayni desen (OLLAMA_BASE_URL bos ise simule davranisa duser). SOAP'tan
 * farkli olarak format:json ISTEMIYOR -- tek bir serbest metin oneri yeterli,
 * S/O/A/P gibi alan ayristirmasi yok.
 */
@Component
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

    OllamaTreatmentRecommendationAdapter(
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
    public TreatmentRecommendationDraft generate(TreatmentRecommendationInput input) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            return generateViaOllama(input);
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
        return new TreatmentRecommendationDraft(suggestion, true);
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
        return new TreatmentRecommendationDraft(STATUS_NOTE, false);
    }
}
