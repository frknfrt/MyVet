package com.vetos.modules.ai.infrastructure.adapter;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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

class OllamaTreatmentRecommendationAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, Clock.systemUTC());

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    private HttpServer stubServer;
    private String stubResponseBody;
    private String capturedBody;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/api/generate", ex -> {
            capturedBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

    private String stubUrl() {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort();
    }

    private TreatmentRecommendationInput anInput() {
        return new TreatmentRecommendationInput(
            "Hafif gastrit supheli",
            "HASTA BİLGİSİ Tür: Köpek | Kritik uyarı: Penisilin alerjisi"
        );
    }

    @Test
    void should_returnSimulatedDraft_when_baseUrlIsBlank() {
        TreatmentRecommendationPort adapter = new OllamaTreatmentRecommendationAdapter("", "llama3.1", new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.suggestionText()).contains("AI modeli henuz baglanmadi");
        assertThat(draft.modelVersion()).isEqualTo("llama3.1");
    }

    @Test
    void should_returnParsedSuggestion_when_ollamaRespondsSuccessfully() {
        stubResponseBody = "{\"response\":\"Diyet degisikligi ve 1 hafta kontrol onerilir.\"}";
        TreatmentRecommendationPort adapter = new OllamaTreatmentRecommendationAdapter(stubUrl(), "llama3.1", new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("Diyet degisikligi ve 1 hafta kontrol onerilir.");
        assertThat(draft.modelVersion()).isEqualTo("llama3.1");
    }

    @Test
    void should_returnSimulatedDraft_when_ollamaCallFails() {
        stubServer.stop(0);
        TreatmentRecommendationPort adapter = new OllamaTreatmentRecommendationAdapter(stubUrl(), "llama3.1", new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
    }

    @Test
    void requestsAWideContextWindow_soTheHistoryIsNotCutOff() {
        stubResponseBody = "{\"response\":\"ok\"}";
        new OllamaTreatmentRecommendationAdapter(stubUrl(), "llama3.1", new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults())
            .generate(anInput());

        assertThat(capturedBody).contains("\"num_ctx\":16384");
    }
}
