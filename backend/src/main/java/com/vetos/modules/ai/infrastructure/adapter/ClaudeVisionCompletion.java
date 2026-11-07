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
 *
 * BULGU (2026-10-31, canli loglarindan): "Sadece JSON don, markdown ekleme" talimatina ragmen
 * Claude bazen yaniti ```json ... ``` seklinde bir markdown kod blogu icine SARIYOR. Bu durumda
 * ham metin "`" ile basliyor ve objectMapper.readTree bunu JSON olarak ayristiramayip hata
 * firlatiyor -- adaptor bunu "model baglanamadi" diye yorumlup kullaniciya yanlis bir mesaj
 * gosteriyordu (gercek sebep: gecerli JSON var ama kod blogu ile sarilmis). stripMarkdownFence
 * bu sarmalayiciyi JSON ayristirmadan ONCE temizler.
 */
final class ClaudeVisionCompletion {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;

    private ClaudeVisionCompletion() {}

    static JsonNode completeJson(RestClient restClient, ObjectMapper objectMapper, String apiUrl, String apiKey,
                                  String model, String system, List<OcrImageInput> images, String instruction) throws Exception {
        return objectMapper.readTree(
            completeText(restClient, objectMapper, apiUrl, apiKey, model, system, images, instruction, MAX_TOKENS));
    }

    /**
     * Dijital onay formlari (2026-11-07): ayristirmayi cagirana birakan varyant -- markdown kod blogu
     * temizlenmis ham metni doner. Uzun formlar icin maxTokens cagiran tarafindan secilir.
     */
    static String completeText(RestClient restClient, ObjectMapper objectMapper, String apiUrl, String apiKey,
                               String model, String system, List<OcrImageInput> images, String instruction,
                               int maxTokens) throws Exception {
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
            "max_tokens", maxTokens,
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
        return stripMarkdownFence(extractText(root));
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

    /**
     * Claude'un yaniti "```json\n{...}\n```" (veya dil etiketsiz "```\n{...}\n```") seklinde bir
     * markdown kod blogu icine sarmis olabilecegi durumu ele alir -- sarilmamissa metni oldugu gibi
     * (sadece trim edilmis) doner, bu yuzden normal (sarilmamis) yanitlar icin DAVRANIS DEGISMEZ.
     */
    static String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int contentStart = firstNewline >= 0 ? firstNewline + 1 : trimmed.length();
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence > contentStart) {
            return trimmed.substring(contentStart, closingFence).trim();
        }
        // Kapanis ``` bulunamadi (yanit kesilmis olabilir) -- en azindan acilis satirini at.
        return trimmed.substring(contentStart).trim();
    }
}
