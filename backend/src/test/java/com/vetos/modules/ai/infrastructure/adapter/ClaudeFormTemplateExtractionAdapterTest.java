package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.FormTemplateExtractionDraft;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** Dijital onay formlari Task 3 -- yerel stub sunucu (gercek API cagrisi yok). */
class ClaudeFormTemplateExtractionAdapterTest {

    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(new SimpleMeterRegistry(), Clock.systemUTC());

    private HttpServer stubServer;
    private String stubResponseBody;
    private int stubStatus = 200;
    private String capturedBody;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/messages", ex -> {
            capturedBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] payload = stubResponseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(stubStatus, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private ClaudeFormTemplateExtractionAdapter adapter(String apiKey) {
        String url = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        return new ClaudeFormTemplateExtractionAdapter(apiKey, "claude-sonnet-5", new ObjectMapper(), url, outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    private static OcrImageInput image() {
        return new OcrImageInput("image/png", "png-bytes".getBytes());
    }

    @Test
    void should_returnNotConnected_when_apiKeyBlank() {
        FormTemplateExtractionDraft draft = adapter("").extract(image());

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.rawJson()).isNull();
        assertThat(capturedBody).isNull();
    }

    @Test
    void should_returnRawJson_withMarkdownFenceStripped() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"```json\\n{\\\"title\\\":\\\"Form\\\"}\\n```\"}]}";

        FormTemplateExtractionDraft draft = adapter("key").extract(image());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.rawJson()).isEqualTo("{\"title\":\"Form\"}");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
        assertThat(capturedBody).contains("\"media_type\":\"image/png\"").contains("\"max_tokens\":8192");
    }

    @Test
    void should_returnNotConnected_when_apiFails() {
        stubStatus = 500;
        stubResponseBody = "{\"error\":\"x\"}";

        FormTemplateExtractionDraft draft = adapter("key").extract(image());

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.rawJson()).isNull();
    }
}
