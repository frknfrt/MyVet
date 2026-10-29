package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.LabResultOcrDraft;
import com.vetos.modules.ai.domain.LabResultOcrInput;
import com.vetos.modules.ai.domain.LabResultOcrPort;
import com.vetos.modules.ai.domain.OcrLabItem;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29,
 * docs/superpowers/plans/2026-10-29-lab-goruntuleme-ai-foto-okuma.md Task 4).
 * OllamaDiagnosisSuggestionAdapter ile AYNI desen (OLLAMA_BASE_URL bos ise
 * simule davranisa duser) -- fark: metin degil GORSEL(ler) gonderir
 * (Ollama /api/generate "images" alani, base64) ve "format":"json" ile
 * yapilandirilmis cikis ister. Model cikisi gecersiz JSON ise (kucuk yerel
 * modeller hata yapabilir) fallback'e duser -- hekim elle doldurur.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
@Slf4j
class OllamaLabResultOcrAdapter implements LabResultOcrPort {

    private static final String PROMPT = """
        Sana bir veteriner laboratuvar sonuc raporunun fotografi verilecek. Bu rapordaki \
        HER parametre satirini oku ve asagidaki KESIN JSON semasinda, BASKA HICBIR METIN \
        EKLEMEDEN don:
        {"items":[{"parameterName":"...","value":"...","unit":"...","referenceRange":"..."}]}
        Kurallar:
        - parameterName: raporda yazan parametre adi (orn. "WBC", "Glukoz").
        - value: sadece olculen deger (birim olmadan).
        - unit: raporda yaziliysa birim (orn. "10^3/uL", "mg/dL"), yoksa bos string "".
        - referenceRange: raporda yazili referans araligi (orn. "6.0-17.0"), yoksa bos string "".
        - Rapordaki HER satiri bir ogeye cevir, hicbirini atlama.
        - Emin olmadigin bir alani bos string "" birak, UYDURMA.
        - Sadece JSON don, aciklama/markdown ekleme.""";

    private final String baseUrl;
    private final String visionModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    OllamaLabResultOcrAdapter(
        @Value("${ai.ollama.base-url:}") String baseUrl,
        @Value("${ai.ollama.vision-model:llava}") String visionModel,
        @Value("${ai.ollama.vision-timeout-seconds:120}") int visionTimeoutSeconds,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this.baseUrl = baseUrl;
        this.visionModel = visionModel;
        this.objectMapper = objectMapper;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(visionTimeoutSeconds));
    }

    @Override
    public LabResultOcrDraft generate(LabResultOcrInput input) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("lab-ocr", "ollama", () -> generateViaOllama(input)));
        } catch (Exception e) {
            log.warn("Ollama lab sonucu foto-okuma basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private LabResultOcrDraft generateViaOllama(LabResultOcrInput input) throws Exception {
        List<String> images = input.images().stream()
            .map(img -> Base64.getEncoder().encodeToString(img.content()))
            .toList();
        Map<String, Object> body = Map.of(
            "model", visionModel,
            "prompt", PROMPT,
            "images", images,
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
        String modelOutput = envelope.path("response").asText("");
        JsonNode parsed = objectMapper.readTree(modelOutput);

        List<OcrLabItem> items = new ArrayList<>();
        for (JsonNode itemNode : parsed.path("items")) {
            items.add(new OcrLabItem(
                textOrEmpty(itemNode, "parameterName"),
                textOrEmpty(itemNode, "value"),
                textOrEmpty(itemNode, "unit"),
                textOrEmpty(itemNode, "referenceRange")
            ));
        }
        return new LabResultOcrDraft(items, true, visionModel);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private LabResultOcrDraft fallback() {
        return new LabResultOcrDraft(List.of(), false, visionModel);
    }
}
