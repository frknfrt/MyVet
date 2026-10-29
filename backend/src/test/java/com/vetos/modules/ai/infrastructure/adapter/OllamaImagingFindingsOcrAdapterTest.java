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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) -- Task 4. */
class OllamaImagingFindingsOcrAdapterTest {

    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(new SimpleMeterRegistry(), Clock.systemUTC());

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

    private ImagingFindingsOcrPort adapter(String baseUrl) {
        return new OllamaImagingFindingsOcrAdapter(baseUrl, "llava", 5, new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void should_returnSimulatedDraft_when_baseUrlIsBlank() {
        ImagingFindingsOcrPort adapter = adapter("");

        ImagingFindingsOcrDraft draft = adapter.generate(new ImagingFindingsOcrInput(List.of(new OcrImageInput("image/jpeg", "fake-image-bytes".getBytes()))));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.findings()).isEmpty();
        assertThat(draft.modelVersion()).isEqualTo("llava");
    }

    @Test
    void generateViaOllama_parsesFindingsText() {
        stubResponseBody = "{\"response\":\"{\\\"findings\\\":\\\"Sol akciger alt lobda hafif infiltrasyon.\\\"}\"}";
        ImagingFindingsOcrPort adapter = adapter(stubUrl());

        ImagingFindingsOcrDraft draft = adapter.generate(new ImagingFindingsOcrInput(List.of(new OcrImageInput("image/jpeg", "fake-image-bytes".getBytes()))));

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.findings()).isEqualTo("Sol akciger alt lobda hafif infiltrasyon.");
    }

    @Test
    void generateViaOllama_sendsBase64ImagesInRequestBody() {
        stubResponseBody = "{\"response\":\"{\\\"findings\\\":\\\"\\\"}\"}";
        ImagingFindingsOcrPort adapter = adapter(stubUrl());
        byte[] imageBytes = "fake-image-bytes".getBytes();
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(imageBytes);

        adapter.generate(new ImagingFindingsOcrInput(List.of(new OcrImageInput("image/jpeg", imageBytes))));

        assertThat(capturedBody).contains("\"images\"").contains(expectedBase64);
    }

    @Test
    void generateViaOllama_invalidJson_fallsBackGracefully() {
        stubResponseBody = "{\"response\":\"bu bir JSON degil\"}";
        ImagingFindingsOcrPort adapter = adapter(stubUrl());

        ImagingFindingsOcrDraft draft = adapter.generate(new ImagingFindingsOcrInput(List.of(new OcrImageInput("image/jpeg", "fake-image-bytes".getBytes()))));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.findings()).isEmpty();
    }
}
