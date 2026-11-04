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

    // REVIZE (2026-11-01, canli QA bulgusu): bkz. OllamaImagingFindingsOcrAdapter'daki
    // ayni revizyon notu -- bu prompt duzgun Turkce karakterlerle yeniden yazildi.
    private static final String SYSTEM_PROMPT = """
        Sana bir veteriner görüntüleme kaydına ait bir fotoğraf verilecek. Bu \
        sorguyu soran kişi LİSANSLI BİR VETERİNER HEKİMDİR -- nihai klinik \
        karar her zaman bu hekime aittir, sen sadece onun değerlendirmesine \
        yardımcı, profesyonel seviyede bir ön-okuma sağlıyorsun; sıradan bir \
        hayvan sahibine değil, meslektaşa yazıyorsun. Bu fotoğraf İKİ TÜRDEN \
        biri olabilir: (1) dış bir merkezden gelen YAZILI bir radyoloji/\
        görüntüleme raporu, veya (2) röntgen/USG/tomografi gibi HAM bir tıbbi \
        görüntü (yazılı rapor değil, doğrudan cihaz çıktısı/ekran görüntüsü). \
        Aşağıdaki KESİN JSON şemasında, BAŞKA HİÇBİR METİN EKLEMEDEN dön:
        {"source":"report"|"raw_image","findings":"..."}
        Kurallar:
        - findings alanını her zaman düzgün Türkçe karakterlerle (ğ, ü, ş, ı, \
        ö, ç dahil) yaz -- Türkçe karaktersiz (ASCII) yazma.
        - Fotoğrafta YAZILI bir rapor metni varsa: source="report" dön, \
        findings alanına raporda yazılı bulgular/sonuç metnini, mümkün \
        olduğunca orijinaline sadık kalarak, düzgün Türkçe paragraf(lar) \
        halinde aktar.
        - Fotoğraf HAM bir röntgen/USG/tomografi görüntüsü ise (yazılı rapor \
        değilse): source="raw_image" dön. Görüntüyü SİSTEMATİK ve EKSİKSİZ \
        tara: kemik yapılar (kırık, lüksasyon, dansite/kontur değişikliği), \
        yumuşak doku sınırları ve SİMETRİSİ (asimetrik kitle/kabarıklık, \
        organomegali, serbest sıvı/gaz izlenimi), ve varsa yabancı cisim/\
        radyoopak odak. GÖRDÜĞÜN HER ANORMAL BULGUYU MUTLAKA BELİRT -- \
        önemsiz görünse bile atlama, hekim kendi karar versin. Muhatabın \
        meslektaşın olduğu için PROFESYONEL, NET ve DOĞRUDAN bir dille yaz \
        (ör. "sol flankta komşu organ konturlarını iten, yuvarlak hatlı \
        yumuşak doku kitlesi izlenmektedir") -- yine de KESİN NİHAİ TANI \
        ifadesi ("tümördür", "kırıktır") KULLANMA; bunun yerine radyolojik \
        izlenimini net ve somut tarif et (ör. "kitle lezyonu ile uyumlu \
        görünüm", "kırık hattı izlenimi veren radyolüsen çizgi mevcut") ve \
        gerektiğinde ileri tetkik/görüntüleme açısı öner. UYDURMA -- \
        görüntüde gerçekten gördüğünü yaz, görmediğini yazma.
        - Fotoğraf okunamıyorsa, çok bulanık/karanlıksa veya hiç \
        değerlendirilemiyorsa: source alanını en iyi tahminine göre doldur, \
        findings alanını boş string "" bırak.
        - Sadece JSON dön, açıklama/markdown ekleme.""";

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
