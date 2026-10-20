package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.vetos.platform.resilience.OutageFailurePredicate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiCircuitBreakerAdapterTest {

    private static final String OK_BODY = """
        {"content":[{"type":"tool_use","input":{"subjective":"s","objective":"o","assessment":"a","plan":"p"}}]}
        """;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 500;
    private SimpleMeterRegistry meters;
    private IntegrationOutcomes outcomes;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            requests.incrementAndGet();
            byte[] body = (status == 200 ? OK_BODY : "{}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        meters = new SimpleMeterRegistry();
        outcomes = new IntegrationOutcomes(meters, Clock.systemUTC());
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(200)).permittedNumberOfCallsInHalfOpenState(2)
            .recordException(new OutageFailurePredicate())
            .build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ClaudeSoapGenerationAdapter adapter() {
        return new ClaudeSoapGenerationAdapter("k", "m", new ObjectMapper(),
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages", outcomes, registry);
    }

    private double failures() {
        var c = meters.find("vetly.integration.calls").tag("integration", "ai").tag("outcome", "failure").counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void should_openAfterOutages_and_skipHttpWhileOpen() {
        var adapter = adapter();
        for (int i = 0; i < 4; i++) {
            assertThat(adapter.generate(new AudioTranscript("metin")).modelConnected()).isFalse();
        }
        assertThat(registry.circuitBreaker("ai").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = requests.get();
        double failuresBefore = failures();

        SoapDraft draft = adapter.generate(new AudioTranscript("metin"));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(requests.get()).isEqualTo(before);          // HTTP gitmedi
        assertThat(failures()).isEqualTo(failuresBefore);      // acik devre IntegrationOutcomes'a yazilmadi
    }

    @Test
    void should_closeAfterHalfOpenSuccesses() throws Exception {
        var adapter = adapter();
        for (int i = 0; i < 4; i++) {
            adapter.generate(new AudioTranscript("metin"));
        }
        Thread.sleep(250);
        status = 200;
        assertThat(adapter.generate(new AudioTranscript("metin")).modelConnected()).isTrue();
        assertThat(adapter.generate(new AudioTranscript("metin")).modelConnected()).isTrue();
        assertThat(registry.circuitBreaker("ai").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void should_notOpen_onClientErrors() {
        status = 400;
        var adapter = adapter();
        for (int i = 0; i < 6; i++) {
            adapter.generate(new AudioTranscript("metin"));
        }
        assertThat(registry.circuitBreaker("ai").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
