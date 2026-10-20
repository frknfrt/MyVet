package com.vetos.modules.ai.infrastructure.adapter;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.HistoryEntry;
import com.vetos.modules.ai.domain.TreatmentRecommendationDraft;
import com.vetos.modules.ai.domain.TreatmentRecommendationInput;
import com.vetos.modules.ai.domain.TreatmentRecommendationPort;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTreatmentRecommendationAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, Clock.systemUTC());

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

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
        TreatmentRecommendationPort adapter = new ClaudeTreatmentRecommendationAdapter("", "claude-sonnet-5", new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());

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
            new ClaudeTreatmentRecommendationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

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
            new ClaudeTreatmentRecommendationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
    }

    @Test
    void should_skipThinkingBlockAndReturnTextBlock_when_extendedThinkingIsOn() {
        // Regresyon testi -- 2026-09-09'da bulunan bug: content[0] "thinking" tipinde
        // oldugunda ("text" alani yok), eski kod content[0]'i sabit metin blogu
        // varsayip bos oneri donduruyordu. extractText artik type=="text" arayarak
        // buluyor. Bkz. implementation-plan.md.
        stubResponseBody = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"...\",\"signature\":\"x\"},"
            + "{\"type\":\"text\",\"text\":\"IV sivi tedavisi ve analjezik onerilir.\"}],\"stop_reason\":\"end_turn\"}";
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        TreatmentRecommendationPort adapter =
            new ClaudeTreatmentRecommendationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("IV sivi tedavisi ve analjezik onerilir.");
    }
}
