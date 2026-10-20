package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import com.vetos.platform.http.OutboundHttp;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.time.Duration;
import java.util.Map;

/**
 * Anthropic Messages API uzerinden SOAP taslagi uretimi -- OllamaSoapGenerationAdapter
 * ile ayni sozlesme (SoapGenerationPort) ve ayni "kimlik bilgisi yoksa simule et"
 * deseni, sadece cagirdigi API farkli. ai.provider=claude oldugunda aktif olur
 * (bkz. OllamaSoapGenerationAdapter'daki @ConditionalOnProperty).
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@Slf4j
class ClaudeSoapGenerationAdapter implements SoapGenerationPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- bu, dikte edilen/yazilan metnin oldugu gibi Subjective alanina "
        + "aktarilmasidir. Gercek bir Claude API baglantisi eklendiginde (ANTHROPIC_API_KEY) SOAP alanlari "
        + "otomatik olarak yapilandirilacak. Su an icin Objective/Assessment/Plan alanlarini elle doldurun.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinigi icin SOAP (Subjective/Objective/Assessment/Plan) notu yapilandiran bir \
        asistansin. Sana hekimin dikte ettigi veya yazdigi serbest metin verilecek. Bu metni SADECE \
        asagidaki alanlara sahip, gecerli bir JSON nesnesi olarak Turkce yeniden yapilandir: "subjective", \
        "objective", "assessment", "plan". Metinde bir alana ait bilgi yoksa o alani bos string birak. \
        Baska hicbir metin, aciklama veya markdown ekleme -- sadece JSON dondur. Tibbi tani koyma, sadece \
        hekimin soylediklerini SOAP formatina yerlestir.""";

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String TOOL_NAME = "structure_soap_note";

    // SOAP alan adlari + JSON semasinda tekrar eden "string" tip literali --
    // hem input_schema'yi kurarken hem de Claude'un yanitini okurken kullanilir,
    // tek yerden degistirilebilsin diye sabitlendi.
    private static final String FIELD_SUBJECTIVE = "subjective";
    private static final String FIELD_OBJECTIVE = "objective";
    private static final String FIELD_ASSESSMENT = "assessment";
    private static final String FIELD_PLAN = "plan";
    private static final String JSON_TYPE_STRING = "string";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final IntegrationOutcomes outcomes;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    ClaudeSoapGenerationAdapter(
        @Value("${ai.anthropic.api-key:}") String apiKey,
        @Value("${ai.anthropic.model:claude-sonnet-5}") String model,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes,
        CircuitBreakerRegistry circuitBreakers
    ) {
        this(apiKey, model, objectMapper, "https://api.anthropic.com/v1/messages", outcomes, circuitBreakers);
    }

    ClaudeSoapGenerationAdapter(String apiKey, String model, ObjectMapper objectMapper, String apiUrl, IntegrationOutcomes outcomes,
                                  CircuitBreakerRegistry circuitBreakers) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.outcomes = outcomes;
        this.circuitBreaker = circuitBreakers.circuitBreaker("ai");
        this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @Override
    public SoapDraft generate(AudioTranscript transcript) {
        String text = transcript.text() == null ? "" : transcript.text().trim();
        if (apiKey.isBlank() || text.isEmpty()) {
            return fallback(text);
        }
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("soap", "claude", () -> generateViaClaude(text)));
        } catch (Exception e) {
            log.warn("Claude SOAP uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback(text);
        }
    }

    private SoapDraft generateViaClaude(String text) throws Exception {
        // Assistant "prefill" ile JSON'u garanti etme yontemini denedik ama bazi
        // modeller (orn. Sonnet 5) bunu reddediyor ("does not support assistant
        // message prefill", bkz. implementation-plan.md Faz 3). Bunun yerine "tool
        // use" (function calling) kullaniyoruz: modeli serbest metin degil, dogrudan
        // yapilandirilmis bir arac cagrisi parametresi olarak JSON uretmeye
        // zorluyoruz -- markdown'a sarilma riski hic yok, ayrica prefill destegi
        // olmayan modellerde de calisir.
        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                FIELD_SUBJECTIVE, Map.of("type", JSON_TYPE_STRING),
                FIELD_OBJECTIVE, Map.of("type", JSON_TYPE_STRING),
                FIELD_ASSESSMENT, Map.of("type", JSON_TYPE_STRING),
                FIELD_PLAN, Map.of("type", JSON_TYPE_STRING)
            ),
            "required", List.of(FIELD_SUBJECTIVE, FIELD_OBJECTIVE, FIELD_ASSESSMENT, FIELD_PLAN)
        );
        Map<String, Object> tool = Map.of(
            "name", TOOL_NAME,
            "description", "Hekimin dikte ettigi/yazdigi serbest metni SOAP (Subjective/Objective/Assessment/Plan) alanlarina yapilandirir.",
            "input_schema", inputSchema
        );
        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 1024,
            "system", SYSTEM_PROMPT,
            "tools", List.of(tool),
            "tool_choice", Map.of("type", "tool", "name", TOOL_NAME),
            "messages", List.of(Map.of("role", "user", "content", text))
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
        JsonNode fields = null;
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asText())) {
                fields = block.path("input");
                break;
            }
        }
        if (fields == null) {
            throw new IllegalStateException("Claude yanitinda tool_use bloğu bulunamadi: " + raw);
        }

        return new SoapDraft(
            fields.path(FIELD_SUBJECTIVE).asText(""),
            fields.path(FIELD_OBJECTIVE).asText(""),
            fields.path(FIELD_ASSESSMENT).asText(""),
            fields.path(FIELD_PLAN).asText(""),
            true
        );
    }

    private SoapDraft fallback(String text) {
        String subjective = text.isEmpty() ? STATUS_NOTE : text + "\n\n" + STATUS_NOTE;
        return new SoapDraft(subjective, "", "", "", false);
    }
}
