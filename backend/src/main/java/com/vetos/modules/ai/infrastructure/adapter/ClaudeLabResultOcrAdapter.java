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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) --
 * OllamaLabResultOcrAdapter'in Claude Messages API (vision) karsiligi. PROMPT
 * OllamaLabResultOcrAdapter ile AYNI (sadece Ollama'da kullanici "prompt"
 * alaninda gider, burada SYSTEM talimati olarak). ai.provider=claude oldugunda
 * aktif olur (bkz. OllamaLabResultOcrAdapter'daki @ConditionalOnProperty).
 *
 * EKSIK OLURSA NE OLUR (2026-10-29 bulgusu): LabResultOcrPort icin tek
 * implementasyon OllamaLabResultOcrAdapter olsaydi ve o da sadece
 * ai.provider=ollama/bos iken olussaydi, ai.provider=claude olan bir ortamda
 * (ornegin bu projenin canli sunucusu) ReadLabResultFromImageUseCase'in
 * zorunlu LabResultOcrPort bagimliligi icin HICBIR bean olusmaz ve Spring
 * context ACILIRKEN COKER (NoSuchBeanDefinitionException) -- yani bu adaptor
 * "daha iyi olur" degil, canliya cikis icin ZORUNLU.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@Slf4j
class ClaudeLabResultOcrAdapter implements LabResultOcrPort {

    private static final String SYSTEM_PROMPT = """
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

    private static final String INSTRUCTION = "Yukarida verilen laboratuvar raporu fotografini oku ve JSON semasina gore don.";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    ClaudeLabResultOcrAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages", outcomes, circuitBreakers);
    }

    ClaudeLabResultOcrAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl,
                               IntegrationOutcomes outcomes, CircuitBreakerRegistry circuitBreakers) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @Override
    public LabResultOcrDraft generate(LabResultOcrInput input) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("lab-ocr", "claude", () -> generateViaClaude(input)));
        } catch (Exception e) {
            log.warn("Claude lab sonucu foto-okuma basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private LabResultOcrDraft generateViaClaude(LabResultOcrInput input) throws Exception {
        JsonNode parsed = ClaudeVisionCompletion.completeJson(
            restClient, objectMapper, apiUrl, apiKey, model, SYSTEM_PROMPT, input.images(), INSTRUCTION);

        List<OcrLabItem> items = new ArrayList<>();
        for (JsonNode itemNode : parsed.path("items")) {
            items.add(new OcrLabItem(
                textOrEmpty(itemNode, "parameterName"),
                textOrEmpty(itemNode, "value"),
                textOrEmpty(itemNode, "unit"),
                textOrEmpty(itemNode, "referenceRange")
            ));
        }
        return new LabResultOcrDraft(items, true, model);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private LabResultOcrDraft fallback() {
        return new LabResultOcrDraft(List.of(), false, model);
    }
}
