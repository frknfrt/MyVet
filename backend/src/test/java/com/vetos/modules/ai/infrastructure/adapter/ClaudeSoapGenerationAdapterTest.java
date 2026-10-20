package com.vetos.modules.ai.infrastructure.adapter;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
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

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeSoapGenerationAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, Clock.systemUTC());

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    private HttpServer stubServer;
    private String stubResponseBody;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/messages", ex -> {
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

    @Test
    void should_returnSimulatedDraft_when_apiKeyIsBlank() {
        SoapGenerationPort adapter = new ClaudeSoapGenerationAdapter("", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        SoapDraft draft = adapter.generate(new AudioTranscript("Hasta kusuyor, iki gundur"));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.subjective()).contains("AI modeli henuz baglanmadi");
    }

    @Test
    void should_returnParsedFields_when_claudeRespondsSuccessfully() {
        // Gercek adaptor "tool use" (function calling) kullaniyor -- model
        // serbest metin degil, structure_soap_note aracina dogrudan yapilandirilmis
        // JSON input olarak cevap veriyor (bkz. ClaudeSoapGenerationAdapter.generateViaClaude).
        stubResponseBody = "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"structure_soap_note\","
            + "\"input\":{\"subjective\":\"Iki gundur kusma\",\"objective\":\"Ates yok\","
            + "\"assessment\":\"Hafif gastrit\",\"plan\":\"Ac birakma, kontrol\"}}]}";
        SoapGenerationPort adapter = new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        SoapDraft draft = adapter.generate(new AudioTranscript("Hasta kusuyor, iki gundur"));

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.subjective()).isEqualTo("Iki gundur kusma");
        assertThat(draft.objective()).isEqualTo("Ates yok");
        assertThat(draft.assessment()).isEqualTo("Hafif gastrit");
        assertThat(draft.plan()).isEqualTo("Ac birakma, kontrol");
    }

    @Test
    void should_returnParsedFields_when_claudeRespondsWithThinkingBlockBeforeToolUse() {
        // Extended thinking acikken content[0] bir "thinking" blogu olabiliyor --
        // adaptor index degil type=="tool_use" arayarak buluyor (bkz. 2026-09-09
        // tedavi onerisi bug'i, implementation-plan.md'de kayitli).
        stubResponseBody = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"...\"},"
            + "{\"type\":\"tool_use\",\"id\":\"toolu_02\",\"name\":\"structure_soap_note\","
            + "\"input\":{\"subjective\":\"S\",\"objective\":\"O\",\"assessment\":\"A\",\"plan\":\"P\"}}]}";
        SoapGenerationPort adapter = new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        SoapDraft draft = adapter.generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.subjective()).isEqualTo("S");
        assertThat(draft.plan()).isEqualTo("P");
    }

    @Test
    void should_returnSimulatedDraft_when_claudeCallFails() {
        stubServer.stop(0);
        SoapGenerationPort adapter = new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        SoapDraft draft = adapter.generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(draft.modelConnected()).isFalse();
    }

    @Test
    void should_returnSimulatedDraft_when_transcriptIsEmpty() {
        SoapGenerationPort adapter = new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults());

        SoapDraft draft = adapter.generate(new AudioTranscript(""));

        assertThat(draft.modelConnected()).isFalse();
    }

    @Test
    void should_recordAiDurationAndSuccess_when_claudeResponds() {
        stubResponseBody = "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"structure_soap_note\","
            + "\"input\":{\"subjective\":\"S\",\"objective\":\"O\",\"assessment\":\"A\",\"plan\":\"P\"}}]}";

        new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults())
            .generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(registry.find("vetly.ai.duration").tag("task", "soap").tag("provider", "claude").timer().count()).isEqualTo(1);
        assertThat(calls("ai", "success")).isEqualTo(1);
    }

    @Test
    void should_recordAiFailure_when_claudeUnreachable() {
        stubServer.stop(0);

        new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults())
            .generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(calls("ai", "failure")).isEqualTo(1);
    }

    @Test
    void should_notRecord_when_apiKeyBlank() {
        new ClaudeSoapGenerationAdapter("", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes, CircuitBreakerRegistry.ofDefaults())
            .generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }
}
