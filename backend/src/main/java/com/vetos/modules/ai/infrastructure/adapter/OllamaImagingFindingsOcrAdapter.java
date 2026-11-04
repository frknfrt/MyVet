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

    // REVIZE (2026-11-01, canli QA bulgusu): bu prompt onceki (Task 4/5)
    // konvansiyonu taklit ederek Turkce karaktersiz (ASCII) yazilmisti; bu da
    // modelin kendi ciktisinin da karaktersiz gelmesine yol aciyordu (ornegin
    // "goruntuyu" yerine "görüntüyü"), ürünün geri kalaniyla tutarsiz
    // duruyordu. pom.xml UTF-8 kullandigi ve PatientHistoryFormatter gibi
    // diger AI prompt'lari zaten duzgun Turkce karakter kullandigi icin
    // burada da duzeltildi; modelden de duzgun Turkce karakter kullanmasi
    // ACIKCA istendi.
    private static final String PROMPT = """
        Sana bir veteriner görüntüleme kaydına ait bir fotoğraf verilecek. Bu \
        fotoğraf İKİ TÜRDEN biri olabilir: (1) dış bir merkezden gelen YAZILI \
        bir radyoloji/görüntüleme raporu, veya (2) röntgen/USG/tomografi gibi \
        HAM bir tıbbi görüntü (yazılı rapor değil, doğrudan cihaz çıktısı/ekran \
        görüntüsü). Aşağıdaki KESİN JSON şemasında, BAŞKA HİÇBİR METİN \
        EKLEMEDEN dön:
        {"source":"report"|"raw_image","findings":"..."}
        Kurallar:
        - findings alanını her zaman düzgün Türkçe karakterlerle (ğ, ü, ş, ı, \
        ö, ç dahil) yaz -- Türkçe karaktersiz (ASCII) yazma.
        - Fotoğrafta YAZILI bir rapor metni varsa: source="report" dön, \
        findings alanına raporda yazılı bulgular/sonuç metnini, mümkün \
        olduğunca orijinaline sadık kalarak, düzgün Türkçe paragraf(lar) \
        halinde aktar.
        - Fotoğraf HAM bir röntgen/USG/tomografi görüntüsü ise (yazılı rapor \
        değilse): source="raw_image" dön, findings alanına görüntüde gözle \
        görülen şeyleri DİKKATLİ ve HEDGE'li (kesin olmayan) bir dille yaz -- \
        örnek ifadeler: "...izlenimi vermektedir", "...gibi görünmektedir", \
        "ileri değerlendirme önerilir". KESİN tanı ifadeleri ("kırık var", \
        "tümör var", "normaldir" gibi kesin sonuç cümleleri) KULLANMA -- bu \
        sadece görsel bir ön-izlenimdir, nihai teşhis her zaman veteriner \
        hekime aittir. UYDURMA -- görüntüde gerçekten gördüğünü yaz, \
        görmediğini yazma.
        - Fotoğraf okunamıyorsa, çok bulanık/karanlıksa veya hiç \
        değerlendirilemiyorsa: source alanını en iyi tahminine göre doldur, \
        findings alanını boş string "" bırak.
        - Sadece JSON dön, açıklama/markdown ekleme.""";

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
