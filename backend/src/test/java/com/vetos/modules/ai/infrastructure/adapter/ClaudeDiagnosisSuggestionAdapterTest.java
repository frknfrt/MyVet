package com.vetos.modules.ai.infrastructure.adapter;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.DiagnosisSuggestionDraft;
import com.vetos.modules.ai.domain.DiagnosisSuggestionInput;
import com.vetos.modules.ai.domain.DiagnosisSuggestionPort;
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

/**
 * ClaudeTreatmentRecommendationAdapterTest ile birebir ayni desen -- ayni
 * duz-metin ayristirma yolu (extractText), ayni stub-server yaklasimi.
 */
class ClaudeDiagnosisSuggestionAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, Clock.systemUTC());

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    private HttpServer stubServer;
    private String stubResponseBody;
    private Headers capturedHeaders;
    private String capturedBody;
    private final java.util.Deque<String> queued = new java.util.ArrayDeque<>();
    private final List<String> bodies = new java.util.ArrayList<>();

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/messages", ex -> {
            capturedHeaders = ex.getRequestHeaders();
            capturedBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(capturedBody);
            byte[] payload = (queued.isEmpty() ? stubResponseBody : queued.poll()).getBytes(StandardCharsets.UTF_8);
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
            "HASTA BİLGİSİ Tür: Köpek | Kritik uyarı: Penisilin alerjisi"
        );
    }

    @Test
    void should_returnSimulatedDraft_when_apiKeyIsBlank() {
        DiagnosisSuggestionPort adapter = new ClaudeDiagnosisSuggestionAdapter("", "claude-sonnet-5", new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());

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
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

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
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

        DiagnosisSuggestionDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("Olasi akut gastroenterit.");
    }

    @Test
    void should_returnSimulatedDraft_when_claudeCallFails() {
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        stubServer.stop(0);
        DiagnosisSuggestionPort adapter =
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

        DiagnosisSuggestionDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
    }

    @Test
    void promptIncludesPatientHistoryBlock() {
        stubResponseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        DiagnosisSuggestionPort adapter =
            new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());

        adapter.generate(anInput());

        assertThat(capturedBody).contains("HASTA BİLGİSİ Tür: Köpek | Kritik uyarı: Penisilin alerjisi")
            .contains("son 1 yillik gecmisi");
    }

    private DiagnosisSuggestionPort connected() {
        String stubUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        return new ClaudeDiagnosisSuggestionAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl, outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    private static String reply(String text, String stopReason) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"stop_reason\":\"" + stopReason + "\"}";
    }

    @Test
    void requestsAllow2048TokensPerPart_andAskForConciseAnswers() {
        stubResponseBody = reply("ok", "end_turn");

        connected().generate(anInput());

        assertThat(capturedBody).contains("\"max_tokens\":2048").contains("Kisa ve oz yaz");
    }

    @Test
    void continuesOnce_whenAnswerIsCutAtTheLimit_andJoinsBothParts() {
        queued.add(reply("1. Gastrit, 2. Pank", "max_tokens"));
        queued.add(reply("reatit", "end_turn"));

        String text = connected().generate(anInput()).suggestionText();

        assertThat(text).isEqualTo("1. Gastrit, 2. Pankreatit");
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(1)).contains("\"role\":\"assistant\"").contains("1. Gastrit, 2. Pank")
            .contains("Kaldigin yerden");
    }

    @Test
    void marksTheAnswer_whenTheContinuationIsAlsoCut() {
        queued.add(reply("Birinci", "max_tokens"));
        queued.add(reply(" ikinci", "max_tokens"));

        String text = connected().generate(anInput()).suggestionText();

        assertThat(text).isEqualTo("Birinci ikinci\n\n(yanit uzunluk sinirinda kesildi)");
        assertThat(bodies).hasSize(2);
    }

    @Test
    void doesNotContinue_whenAnswerIsComplete() {
        stubResponseBody = reply("Tam cevap", "end_turn");

        assertThat(connected().generate(anInput()).suggestionText()).isEqualTo("Tam cevap");
        assertThat(bodies).hasSize(1);
    }
}
