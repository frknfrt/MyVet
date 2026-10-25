package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Tanı ve tedavi önerisi için Claude metin çağrısı (2026-10-25).
 * Cevap uzunluk sınırına takılırsa bir kez "kaldığın yerden devam et" denir ve iki parça birleştirilir;
 * hekim yarım cevap görmesin. İkinci parça da kesilirse sonuna not düşülür (sonsuz döngü yok).
 * Süre: nginx /api isteğini en fazla 90 sn bekler. Kapasite iki parçaya bölünür (2048 + 2048) ve ilk parça
 * CONTINUE_WITHIN süresinden uzun sürdüyse devam istenmez, not düşülür (docs/deployment.md AI notu).
 */
@Slf4j
final class ClaudeTextCompletion {

    /** Parça başına sınır; toplam kapasite iki parçayla 4096. */
    static final int MAX_TOKENS = 2048;
    static final Duration CONTINUE_WITHIN = Duration.ofSeconds(40);
    static final String CONTINUE_PROMPT = "Kaldigin yerden, onceki metni tekrar etmeden devam et.";
    static final String CUT_NOTE = "\n\n(yanit uzunluk sinirinda kesildi)";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private ClaudeTextCompletion() {}

    static String complete(RestClient restClient, ObjectMapper objectMapper, String apiUrl, String apiKey, String model,
                           String system, String prompt) throws Exception {
        return complete(restClient, objectMapper, apiUrl, apiKey, model, system, prompt, System::nanoTime);
    }

    static String complete(RestClient restClient, ObjectMapper objectMapper, String apiUrl, String apiKey, String model,
                           String system, String prompt, LongSupplier nanoClock) throws Exception {
        long start = nanoClock.getAsLong();
        Reply first = call(restClient, objectMapper, apiUrl, apiKey, model, system,
            List.of(Map.of("role", "user", "content", prompt)));
        if (!first.cut()) {
            return first.text();
        }
        long elapsed = nanoClock.getAsLong() - start;
        // Boş parça: boş asistan mesajı API hatası verir. Uzun süren parça: nginx 90 sn sınırı.
        if (first.text().isBlank() || elapsed > CONTINUE_WITHIN.toNanos()) {
            log.warn("Claude cevabi kesildi; devam istenmeden not eklendi (ilk parca {} sn, bos={})", elapsed / 1_000_000_000L, first.text().isBlank());
            return (first.text().trim() + CUT_NOTE).trim();
        }
        log.warn("Claude cevabi {} sinirinda kesildi, devam isteniyor", MAX_TOKENS);
        Reply rest = call(restClient, objectMapper, apiUrl, apiKey, model, system, List.of(
            Map.of("role", "user", "content", prompt),
            Map.of("role", "assistant", "content", first.text()),
            Map.of("role", "user", "content", CONTINUE_PROMPT)));
        String joined = (first.text() + rest.text()).trim();
        if (rest.cut()) {
            log.warn("Claude devam cevabi da kesildi; not eklendi");
            return joined + CUT_NOTE;
        }
        return joined;
    }

    private static Reply call(RestClient restClient, ObjectMapper objectMapper, String apiUrl, String apiKey, String model,
                              String system, List<Map<String, String>> messages) throws Exception {
        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", MAX_TOKENS,
            "system", system,
            "messages", messages
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
        return new Reply(extractText(root), "max_tokens".equals(root.path("stop_reason").asText()));
    }

    /**
     * content[0] her zaman metin değil: extended thinking açıkken önce "thinking" bloğu gelir.
     * İlk type=="text" bloğu alınır (2026-09-09 boş öneri hatası). Devam isteğinde baştaki boşluk korunur.
     */
    private static String extractText(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText("");
            }
        }
        return "";
    }

    private record Reply(String text, boolean cut) {}
}
