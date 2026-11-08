package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class OllamaFormTemplateExtractionAdapterTest {
    private HttpServer server;
    private boolean stall;
    private volatile String captured;
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(new SimpleMeterRegistry(), Clock.systemUTC());

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", ex -> {
            captured = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (stall) {
                try { releaseResponse.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                ex.close();
                return;
            }
            byte[] response = "{\"response\":\"{\\\"title\\\":\\\"Form\\\"}\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, response.length);
            ex.getResponseBody().write(response);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void cleanup() {
        releaseResponse.countDown();
        server.stop(0);
    }

    private OllamaFormTemplateExtractionAdapter adapter(int timeout) {
        return new OllamaFormTemplateExtractionAdapter("http://127.0.0.1:" + server.getAddress().getPort(),
            "vision-test", timeout, new ObjectMapper(), outcomes, CircuitBreakerRegistry.ofDefaults());
    }

    private OcrImageInput image() { return new OcrImageInput("image/png", new byte[]{1, 2, 3}); }

    @Test
    void successfulResponseStillReturnsDraft() {
        var draft = adapter(45).extract(image());
        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.rawJson()).isEqualTo("{\"title\":\"Form\"}");
        assertThat(captured).contains("vision-test");
    }

    @Test
    void acceptedButUnansweredRequestTimesOutToFallback() {
        stall = true;
        var draft = adapter(1).extract(image());
        assertThat(captured).isNotNull();
        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.rawJson()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 61})
    void rejectsTimeoutOutsideSupportedRange(int timeout) {
        assertThatThrownBy(() -> adapter(timeout)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 60");
    }
}
