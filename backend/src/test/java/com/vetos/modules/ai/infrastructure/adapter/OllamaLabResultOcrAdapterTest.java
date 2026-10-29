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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29) -- Task 4. */
class OllamaLabResultOcrAdapterTest {

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

    private LabResultOcrPort adapter(String baseUrl) {
        return new OllamaLabResultOcrAdapter(baseUrl, "llava", 5, new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void should_returnSimulatedDraft_when_baseUrlIsBlank() {
        LabResultOcrPort adapter = adapter("");

        LabResultOcrDraft draft = adapter.generate(new LabResultOcrInput(List.of(new OcrImageInput("image/jpeg", "fake-image-bytes".getBytes()))));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.items()).isEmpty();
        assertThat(draft.modelVersion()).isEqualTo("llava");
    }

    @Test
    void generateViaOllama_parsesStructuredItems() {
        stubResponseBody = "{\"response\":\"{\\\"items\\\":[{\\\"parameterName\\\":\\\"WBC\\\",\\\"value\\\":\\\"12.3\\\",\\\"unit\\\":\\\"10^3/uL\\\",\\\"referenceRange\\\":\\\"6.0-17.0\\\"}]}\"}";
        LabResultOcrPort adapter = adapter(stubUrl());

        LabResultOcrDraft draft = adapter.generate(new LabResultOcrInput(List.of(new OcrImageInput("image/jpeg", "fake-image-bytes".getBytes()))));

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.items()).hasSize(1);
        assertThat(draft.items().get(0).parameterName()).isEqualTo("WBC");
        assertThat(draft.items().get(0).value()).isEqualTo("12.3");
        assertThat(draft.items().get(0).unit()).isEqualTo("10^3/uL");
        assertThat(draft.items().get(0).referenceRange()).isEqualTo("6.0-17.0");
    }

    @Test
    void generateViaOllama_sendsBase64ImagesInRequestBody() {
        stubResponseBody = "{\"response\":\"{\\\"items\\\":[]}\"}";
        LabResultOcrPort adapter = adapter(stubUrl());
        byte[] imageBytes = "fake-image-bytes".getBytes();
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(imageBytes);

        adapter.generate(new LabResultOcrInput(List.of(new OcrImageInput("image/jpeg", imageBytes))));

        assertThat(capturedBody).contains("\"images\"").contains(expectedBase64);
    }

    @Test
    void generateViaOllama_invalidJson_fallsBackGracefully() {
        stubResponseBody = "{\"response\":\"bu bir JSON degil\"}";
        LabResultOcrPort adapter = adapter(stubUrl());

        LabResultOcrDraft draft = adapter.generate(new LabResultOcrInput(List.of(new OcrImageInput("image/jpeg", "fake-image-bytes".getBytes()))));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.items()).isEmpty();
    }
}
