package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.OcrImageInput;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) --
 * Claude Messages API uzerinden GORSEL + METIN istek (vision). ClaudeTextCompletion
 * ile AYNI kimlik dogrulama/istek deseni (x-api-key, anthropic-version header'lari);
 * fark "content" duz metin degil, resim blok(lari) + bir metin talimati iceren bir
 * dizi.
 *
 * ONEMLI (2026-10-29 bulgusu): ai.provider=claude olan ortamlarda, OllamaLabResultOcrAdapter/
 * OllamaImagingFindingsOcrAdapter'in kullandigi AYRI bir "vision model" (ai.ollama.vision-model,
 * orn. llava) konfigurasyonuna ihtiyac YOK -- Anthropic'in guncel modelleri (sonnet/opus/haiku)
 * metin cagrilarinda zaten kullanilan ai.anthropic.model ile ayni anda gorsel girdiyi de
 * destekler. Yanit JSON semasi Ollama'daki "format":"json" karsiligi Claude'da yok, bu yuzden
 * sistem talimatinda istenir; donen ilk metin blogu dogrudan JSON olarak ayristirilir (Ollama'daki
 * gibi CIFT JSON parse GEREKMEZ -- Claude govdesi zaten tek seviye: content[].text).
 */
final class ClaudeVisionCompletion {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;

    private ClaudeVisionCompletion() {}

    static JsonNode completeJson(RestClient restClient, ObjectMapper objectMapper, String apiUrl, String apiKey,
                                  String model, String system, List<OcrImageInput> images, String instruction) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        for (OcrImageInput image : images) {
            content.add(Map.of(
                "type", "image",
                "source", Map.of(
                    "type", "base64",
                    "media_type", image.contentType(),
                    "data", Base64.getEncoder().encodeToString(image.content())
                )
            ));
        }
        content.add(Map.of("type", "text", "text", instruction));

        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", MAX_TOKENS,
            "system", system,
            "messages", List.of(Map.of("role", "user", "content", content))
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
        return objectMapper.readTree(extractText(root));
    }

    /** content[0] her zaman metin degil: extended thinking acikken once "thinking" blogu gelir. */
    private static String extractText(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText("");
            }
        }
        return "";
    }
}
