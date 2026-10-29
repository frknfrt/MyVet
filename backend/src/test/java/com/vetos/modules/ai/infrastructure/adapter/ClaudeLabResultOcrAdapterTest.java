package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.LabResultOcrDraft;
import com.vetos.modules.ai.domain.LabResultOcrInput;
import com.vetos.modules.ai.domain.LabResultOcrPort;
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
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) --
 * ai.provider=claude icin eksik olan LabResultOcrPort implementasyonunun duzeltmesi.
 * OllamaLabResultOcrAdapterTest ile AYNI kapsam, Claude Messages API (vision) icin.
 */
class ClaudeLabResultOcrAdapterTest {

    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(new SimpleMeterRegistry(), Clock.systemUTC());

    private HttpServer stubServer;
    private String stubResponseBody;
    private String capturedBody;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/messages", ex -> {
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
        return "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
    }

    private LabResultOcrPort adapter(String apiKey) {
        return new ClaudeLabResultOcrAdapter(apiKey, "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    private static LabResultOcrInput anInput(byte[] imageBytes) {
        return new LabResultOcrInput(List.of(new OcrImageInput("image/jpeg", imageBytes)));
    }

    @Test
    void should_returnSimulatedDraft_when_apiKeyIsBlank() {
        LabResultOcrPort adapter = new ClaudeLabResultOcrAdapter("", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        LabResultOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.items()).isEmpty();
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void generateViaClaude_parsesStructuredItems() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\""
            + "{\\\"items\\\":[{\\\"parameterName\\\":\\\"WBC\\\",\\\"value\\\":\\\"12.3\\\",\\\"unit\\\":\\\"10^3/uL\\\",\\\"referenceRange\\\":\\\"6.0-17.0\\\"}]}"
            + "\"}]}";
        LabResultOcrPort adapter = adapter("test-api-key");

        LabResultOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.items()).hasSize(1);
        assertThat(draft.items().get(0).parameterName()).isEqualTo("WBC");
        assertThat(draft.items().get(0).value()).isEqualTo("12.3");
        assertThat(draft.items().get(0).unit()).isEqualTo("10^3/uL");
        assertThat(draft.items().get(0).referenceRange()).isEqualTo("6.0-17.0");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void generateViaClaude_sendsBase64ImageWithMediaTypeInRequestBody() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"items\\\":[]}\"}]}";
        LabResultOcrPort adapter = adapter("test-api-key");
        byte[] imageBytes = "fake-image-bytes".getBytes();
        String expectedBase64 = Base64.getEncoder().encodeToString(imageBytes);

        adapter.generate(anInput(imageBytes));

        assertThat(capturedBody).contains("\"type\":\"image\"").contains("\"media_type\":\"image/jpeg\"").contains(expectedBase64);
    }

    @Test
    void generateViaClaude_invalidJson_fallsBackGracefully() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"bu bir JSON degil\"}]}";
        LabResultOcrPort adapter = adapter("test-api-key");

        LabResultOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.items()).isEmpty();
    }

    @Test
    void should_returnSimulatedDraft_when_claudeCallFails() {
        LabResultOcrPort adapter = adapter("test-api-key");
        stubServer.stop(0);

        LabResultOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isFalse();
    }
}
