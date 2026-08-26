package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Lokal Ollama uzerinden gercek LLM cagrisi (OLLAMA_BASE_URL bos ise eski
 * mock davranisina duser -- TwilioNotificationAdapter'daki "kimlik bilgisi/URL
 * yoksa simule et" ile ayni desen, bkz. implementation-plan.md). Ollama disinda
 * bulut tabanli bir saglayici (orn. Anthropic) eklenmek istendiginde,
 * SoapGenerationPort sozlesmesi ve cagiran kod (GenerateSoapDraftUseCase) hic
 * degismeden sadece bu sinif degisir/yeni bir @Component eklenir
 * (@docs/architecture.md Bolum 3).
 */
@Component
@Slf4j
class OllamaSoapGenerationAdapter implements SoapGenerationPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- bu, dikte edilen/yazilan metnin oldugu gibi Subjective alanina "
        + "aktarilmasidir. Gercek bir LLM baglantisi eklendiginde (OLLAMA_BASE_URL) SOAP alanlari otomatik "
        + "olarak yapilandirilacak. Su an icin Objective/Assessment/Plan alanlarini elle doldurun.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinigi icin SOAP (Subjective/Objective/Assessment/Plan) notu yapilandiran bir \
        asistansin. Sana hekimin dikte ettigi veya yazdigi serbest metin verilecek. Bu metni SADECE \
        asagidaki alanlara sahip, gecerli bir JSON nesnesi olarak Turkce yeniden yapilandir: "subjective", \
        "objective", "assessment", "plan". Metinde bir alana ait bilgi yoksa o alani bos string birak. \
        Baska hicbir metin, aciklama veya markdown ekleme -- sadece JSON dondur. Tibbi tani koyma, sadece \
        hekimin soylediklerini SOAP formatina yerlestir.""";

    private final String baseUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    OllamaSoapGenerationAdapter(
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
    public SoapDraft generate(AudioTranscript transcript) {
        String text = transcript.text() == null ? "" : transcript.text().trim();
        if (baseUrl.isBlank() || text.isEmpty()) {
            return fallback(text);
        }
        try {
            return generateViaOllama(text);
        } catch (Exception e) {
            log.warn("Ollama SOAP uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback(text);
        }
    }

    private SoapDraft generateViaOllama(String text) throws Exception {
        Map<String, Object> body = Map.of(
            "model", model,
            "system", SYSTEM_PROMPT,
            "prompt", text,
            "format", "json",
            "stream", false
        );
        String raw = restClient.post()
            .uri(baseUrl + "/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

        JsonNode root = objectMapper.readTree(raw);
        JsonNode fields = objectMapper.readTree(root.path("response").asText("{}"));

        return new SoapDraft(
            fields.path("subjective").asText(""),
            fields.path("objective").asText(""),
            fields.path("assessment").asText(""),
            fields.path("plan").asText(""),
            true
        );
    }

    private SoapDraft fallback(String text) {
        String subjective = text.isEmpty() ? STATUS_NOTE : text + "\n\n" + STATUS_NOTE;
        return new SoapDraft(subjective, "", "", "", false);
    }
}
