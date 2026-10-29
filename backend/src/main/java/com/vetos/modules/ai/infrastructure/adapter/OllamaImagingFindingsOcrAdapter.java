package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.ImagingFindingsOcrDraft;
import com.vetos.modules.ai.domain.ImagingFindingsOcrInput;
import com.vetos.modules.ai.domain.ImagingFindingsOcrPort;
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
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29,
 * docs/superpowers/plans/2026-10-29-lab-goruntuleme-ai-foto-okuma.md Task 4).
 * OllamaLabResultOcrAdapter ile AYNI desen -- fark: tek bir serbest metin
 * (findings) alani doner, parametre listesi yok. Kapsam bilinçli dar: SADECE
 * dis merkezden gelen yazili rapor fotografini okur (OCR), ham goruntuyu
 * (rontgen/USG) AI yorumlamaz (spec 2026-10-28 §3.2).
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
@Slf4j
class OllamaImagingFindingsOcrAdapter implements ImagingFindingsOcrPort {

    private static final String PROMPT = """
        Sana dis bir merkezden gelen yazili bir radyoloji/goruntuleme raporunun \
        fotografi verilecek. Bu rapordaki bulgular/yorum metnini oku ve asagidaki \
        KESIN JSON semasinda, BASKA HICBIR METIN EKLEMEDEN don:
        {"findings":"..."}
        Kurallar:
        - findings: raporda yazili bulgular/sonuc metnini, mumkun oldugunca \
        orijinaline sadik kalarak, duzgun Turkce paragraf(lar) halinde aktar.
        - Bu bir ham rontgen/USG goruntusu ise (yazili rapor degilse) ya da rapor \
        okunamiyorsa findings alanini bos string "" birak -- UYDURMA, tibbi yorum \
        yapma.
        - Sadece JSON don, aciklama/markdown ekleme.""";

    private final String baseUrl;
    private final String visionModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    OllamaImagingFindingsOcrAdapter(
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
    public ImagingFindingsOcrDraft generate(ImagingFindingsOcrInput input) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("imaging-ocr", "ollama", () -> generateViaOllama(input)));
        } catch (Exception e) {
            log.warn("Ollama goruntuleme foto-okuma basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private ImagingFindingsOcrDraft generateViaOllama(ImagingFindingsOcrInput input) throws Exception {
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
        String findings = parsed.path("findings").asText("");
        return new ImagingFindingsOcrDraft(findings, true, visionModel);
    }

    private ImagingFindingsOcrDraft fallback() {
        return new ImagingFindingsOcrDraft("", false, visionModel);
    }
}
