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
 * (findings) alani doner, parametre listesi yok.
 *
 * REVIZE (2026-10-31, Task 11): eskiden kapsam BILINCLI olarak dar tutulup
 * ham goruntu (rontgen/USG) AI tarafindan yorumlanmiyordu (spec 2026-10-28
 * §3.2) -- kullanici talebi ve kendi karari (DIKKATLI/hedge'li aciklama +
 * zorunlu uyari) uzerine bu kisitlama kaldirildi. Model artik ham goruntuyu
 * de gorsel olarak tanimliyor ama KESIN tani KOYMUYOR (prompt'taki kati
 * kurallara bkz.); zorunlu uyari metni ise modelin kendisine degil
 * ReadImagingFindingsFromImageUseCase'teki deterministik koda birakilmistir.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
@Slf4j
class OllamaImagingFindingsOcrAdapter implements ImagingFindingsOcrPort {

    private static final String PROMPT = """
        Sana bir veteriner goruntuleme kaydina ait bir fotograf verilecek. Bu \
        fotograf IKI TURDEN biri olabilir: (1) dis bir merkezden gelen YAZILI \
        bir radyoloji/goruntuleme raporu, veya (2) rontgen/USG/tomografi gibi \
        HAM bir tibbi goruntu (yazili rapor degil, dogrudan cihaz ciktisi/ekran \
        goruntusu). Asagidaki KESIN JSON semasinda, BASKA HICBIR METIN \
        EKLEMEDEN don:
        {"source":"report"|"raw_image","findings":"..."}
        Kurallar:
        - Fotografta YAZILI bir rapor metni varsa: source="report" don, \
        findings alanina raporda yazili bulgular/sonuc metnini, mumkun \
        oldugunca orijinaline sadik kalarak, duzgun Turkce paragraf(lar) \
        halinde aktar.
        - Fotograf HAM bir rontgen/USG/tomografi goruntusu ise (yazili rapor \
        degilse): source="raw_image" don, findings alanina goruntude gozle \
        gorulen seyleri DIKKATLI ve HEDGE'li (kesin olmayan) bir dille yaz -- \
        ornek ifadeler: "...izlenimi vermektedir", "...gibi gorunmektedir", \
        "ileri degerlendirme onerilir". KESIN tani ifadeleri ("kirik var", \
        "tumor var", "normaldir" gibi kesin sonuc cumleleri) KULLANMA -- bu \
        sadece gorsel bir on-izlenimdir, nihai teshis her zaman veteriner \
        hekime aittir. UYDURMA -- goruntude gercekten gordugunu yaz, \
        gormedigini yazma.
        - Fotograf okunamiyorsa, cok bulanik/karanliksa veya hic \
        degerlendirilemiyorsa: source alanini en iyi tahminine gore doldur, \
        findings alanini bos string "" birak.
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
            log.warn("Ollama goruntuleme foto-okuma basarisiz, mock'a duseluyor: {}", OcrFailureLogging.describe(e), e);
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
        boolean rawImageInterpretation = "raw_image".equals(parsed.path("source").asText("report"));
        return new ImagingFindingsOcrDraft(findings, true, visionModel, rawImageInterpretation);
    }

    private ImagingFindingsOcrDraft fallback() {
        return new ImagingFindingsOcrDraft("", false, visionModel, false);
    }
}
