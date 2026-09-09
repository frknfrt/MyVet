package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
import com.vetos.modules.ai.domain.HistoryEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ClaudeTreatmentRecommendationAdapterTest ile birebir ayni desen -- ayni
 * duz-metin ayristirma yolu (extractText), ayni stub-server yaklasimi.
 */
class ClaudeDiagnosisSuggestionAdapterTest {

    private HttpServer stubServer;
    private String stubResponseBody;
    private Headers capturedHeaders;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/messages", ex -> {
            capturedHeaders = ex.getRequestHeaders();
            byte[] payload = stubResponseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private DiagnosisSuggestionInput anInput() {
        return new DiagnosisSuggestionInput(
            "Sahip sari kopuklu kusma bildiriyor",
            "Karin hassasiyeti mevcut",
            "Gastrointestinal: Anormal (hassasiyet)",
            "Nabiz: 185 /dk",
            List.of(new HistoryEntry(Instant.now().toString(), "Gecmis degerlendirme", "Gecmis plan"))
        );
    }

    @Test
    void should_returnSimulatedDraft_when_apiKeyIsBlank() {
        DiagnosisSuggestionPort adapter = new ClaudeDiagnosisSuggestionAdapter("", "claude-sonnet-5", new ObjectMapper());

        DiagnosisSuggestionDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.suggestionText()).contains("AI modeli henuz baglanmadi");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void should_returnParsedSuggestion_when_claudeRespondsSuccessfully() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"Gastrointestinal yabanci cisim ve pankreatit ayirici tanida on planda.\"}]}";
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        DiagnosisSuggestionPort adapter =
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl);

        DiagnosisSuggestionDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("Gastrointestinal yabanci cisim ve pankreatit ayirici tanida on planda.");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
        assertThat(capturedHeaders.getFirst("x-api-key")).isEqualTo("test-api-key");
        assertThat(capturedHeaders.getFirst("anthropic-version")).isEqualTo("2023-06-01");
    }

    @Test
    void should_skipThinkingBlockAndReturnTextBlock_when_extendedThinkingIsOn() {
        stubResponseBody = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"...\",\"signature\":\"x\"},"
            + "{\"type\":\"text\",\"text\":\"Olasi akut gastroenterit.\"}],\"stop_reason\":\"end_turn\"}";
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        DiagnosisSuggestionPort adapter =
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl);

        DiagnosisSuggestionDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("Olasi akut gastroenterit.");
    }

    @Test
    void should_returnSimulatedDraft_when_claudeCallFails() {
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        stubServer.stop(0);
        DiagnosisSuggestionPort adapter =
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl);

        DiagnosisSuggestionDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
    }
}
