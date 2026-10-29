package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.ImagingFindingsOcrDraft;
import com.vetos.modules.ai.domain.ImagingFindingsOcrInput;
import com.vetos.modules.ai.domain.ImagingFindingsOcrPort;
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
 * ai.provider=claude icin eksik olan ImagingFindingsOcrPort implementasyonunun
 * duzeltmesi. OllamaImagingFindingsOcrAdapterTest ile AYNI kapsam, Claude
 * Messages API (vision) icin.
 */
class ClaudeImagingFindingsOcrAdapterTest {

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

    private ImagingFindingsOcrPort adapter(String apiKey) {
        return new ClaudeImagingFindingsOcrAdapter(apiKey, "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    private static ImagingFindingsOcrInput anInput(byte[] imageBytes) {
        return new ImagingFindingsOcrInput(List.of(new OcrImageInput("image/jpeg", imageBytes)));
    }

    @Test
    void should_returnSimulatedDraft_when_apiKeyIsBlank() {
        ImagingFindingsOcrPort adapter = new ClaudeImagingFindingsOcrAdapter("", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        ImagingFindingsOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.findings()).isEmpty();
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void generateViaClaude_parsesFindings() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\""
            + "{\\\"findings\\\":\\\"Sol akciger alt lobda hafif infiltrasyon.\\\"}"
            + "\"}]}";
        ImagingFindingsOcrPort adapter = adapter("test-api-key");

        ImagingFindingsOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.findings()).isEqualTo("Sol akciger alt lobda hafif infiltrasyon.");
        assertThat(draft.modelVersion()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void generateViaClaude_sendsBase64ImageWithMediaTypeInRequestBody() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"findings\\\":\\\"\\\"}\"}]}";
        ImagingFindingsOcrPort adapter = adapter("test-api-key");
        byte[] imageBytes = "fake-image-bytes".getBytes();
        String expectedBase64 = Base64.getEncoder().encodeToString(imageBytes);

        adapter.generate(anInput(imageBytes));

        assertThat(capturedBody).contains("\"type\":\"image\"").contains("\"media_type\":\"image/jpeg\"").contains(expectedBase64);
    }

    @Test
    void generateViaClaude_invalidJson_fallsBackGracefully() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"bu bir JSON degil\"}]}";
        ImagingFindingsOcrPort adapter = adapter("test-api-key");

        ImagingFindingsOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.findings()).isEmpty();
    }

    @Test
    void should_returnSimulatedDraft_when_claudeCallFails() {
        ImagingFindingsOcrPort adapter = adapter("test-api-key");
        stubServer.stop(0);

        ImagingFindingsOcrDraft draft = adapter.generate(anInput("fake-image-bytes".getBytes()));

        assertThat(draft.modelConnected()).isFalse();
    }
}
