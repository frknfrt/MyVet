package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.HistoryEntry;
import com.vetos.modules.ai.domain.TreatmentRecommendationDraft;
import com.vetos.modules.ai.domain.TreatmentRecommendationInput;
import com.vetos.modules.ai.domain.TreatmentRecommendationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTreatmentRecommendationAdapterTest {

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

    private TreatmentRecommendationInput anInput() {
        return new TreatmentRecommendationInput(
            "Hafif gastrit supheli",
            List.of(new HistoryEntry(Instant.now().toString(), "Gecmis degerlendirme", "Gecmis plan"))
        );
    }

    @Test
    void should_returnSimulatedDraft_when_apiKeyIsBlank() {
        TreatmentRecommendationPort adapter = new ClaudeTreatmentRecommendationAdapter("", "claude-sonnet-5", new ObjectMapper());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.suggestionText()).contains("AI modeli henuz baglanmadi");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void should_returnParsedSuggestion_when_claudeRespondsSuccessfully() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"Diyet degisikligi ve 1 hafta kontrol onerilir.\"}]}";
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        TreatmentRecommendationPort adapter =
            new ClaudeTreatmentRecommendationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl);

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("Diyet degisikligi ve 1 hafta kontrol onerilir.");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
        assertThat(capturedHeaders.getFirst("x-api-key")).isEqualTo("test-api-key");
        assertThat(capturedHeaders.getFirst("anthropic-version")).isEqualTo("2023-06-01");
    }

    @Test
    void should_returnSimulatedDraft_when_claudeCallFails() {
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        stubServer.stop(0);
        TreatmentRecommendationPort adapter =
            new ClaudeTreatmentRecommendationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl);

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
    }
}
