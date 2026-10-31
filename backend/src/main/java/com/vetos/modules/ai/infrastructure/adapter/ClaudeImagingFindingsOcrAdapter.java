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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) --
 * OllamaImagingFindingsOcrAdapter'in Claude Messages API (vision) karsiligi.
 * ClaudeLabResultOcrAdapter ile AYNI desen -- fark: tek bir serbest metin
 * (findings) alani doner, parametre listesi yok. ai.provider=claude oldugunda
 * aktif olur. Bu adaptorun neden ZORUNLU oldugu icin bkz.
 * ClaudeLabResultOcrAdapter javadoc'u (2026-10-29 bulgusu).
 *
 * REVIZE (2026-10-31, Task 11): bkz. OllamaImagingFindingsOcrAdapter'daki
 * ayni revizyon notu -- ham goruntu (rontgen/USG) artik DIKKATLI/hedge'li
 * bir dille AI tarafindan tanimlaniyor, zorunlu uyari metni ise
 * ReadImagingFindingsFromImageUseCase'te deterministik olarak eklenir.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@Slf4j
class ClaudeImagingFindingsOcrAdapter implements ImagingFindingsOcrPort {

    private static final String SYSTEM_PROMPT = """
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

    private static final String INSTRUCTION = "Yukarida verilen goruntuleme fotografini oku ve JSON semasina gore don.";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    ClaudeImagingFindingsOcrAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages", outcomes, circuitBreakers);
    }

    ClaudeImagingFindingsOcrAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl,
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
    public ImagingFindingsOcrDraft generate(ImagingFindingsOcrInput input) {
        if (apiKey.isBlank()) {
            return fallback();
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("imaging-ocr", "claude", () -> generateViaClaude(input)));
        } catch (Exception e) {
            log.warn("Claude goruntuleme foto-okuma basarisiz, mock'a duseluyor: {}", OcrFailureLogging.describe(e), e);
            return fallback();
        }
    }

    private ImagingFindingsOcrDraft generateViaClaude(ImagingFindingsOcrInput input) throws Exception {
        JsonNode parsed = ClaudeVisionCompletion.completeJson(
            restClient, objectMapper, apiUrl, apiKey, model, SYSTEM_PROMPT, input.images(), INSTRUCTION);
        String findings = parsed.path("findings").asText("");
        boolean rawImageInterpretation = "raw_image".equals(parsed.path("source").asText("report"));
        return new ImagingFindingsOcrDraft(findings, true, model, rawImageInterpretation);
    }

    private ImagingFindingsOcrDraft fallback() {
        return new ImagingFindingsOcrDraft("", false, model, false);
    }
}
