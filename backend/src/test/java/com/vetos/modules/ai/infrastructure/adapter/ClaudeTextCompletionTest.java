package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.platform.http.OutboundHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTextCompletionTest {

    private HttpServer stubServer;
    private final Deque<String> queued = new ArrayDeque<>();
    private final List<String> bodies = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/v1/messages", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = queued.poll().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stop() {
        stubServer.stop(0);
    }

    private static String reply(String text, String stopReason) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"stop_reason\":\"" + stopReason + "\"}";
    }

    /** Her okumada saati verilen kadar ilerletir: ilk çağrının süresini taklit eder. */
    private static LongSupplier clockAdvancing(Duration perRead) {
        long[] now = {0};
        return () -> {
            long t = now[0];
            now[0] += perRead.toNanos();
            return t;
        };
    }

    private String complete(LongSupplier clock) throws Exception {
        String url = "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1/messages";
        return ClaudeTextCompletion.complete(OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(10)),
            new ObjectMapper(), url, "k", "m", "sys", "soru", clock);
    }

    @Test
    void continues_whenFirstPartWasCutQuickly() throws Exception {
        queued.add(reply("Bir", "max_tokens"));
        queued.add(reply("iki", "end_turn"));

        assertThat(complete(clockAdvancing(Duration.ofSeconds(20)))).isEqualTo("Biriki");
        assertThat(bodies).hasSize(2);
    }

    @Test
    void skipsContinuationAndMarksTheAnswer_whenFirstPartTookTooLong() throws Exception {
        queued.add(reply("Bir", "max_tokens"));

        assertThat(complete(clockAdvancing(Duration.ofSeconds(45)))).isEqualTo("Bir" + ClaudeTextCompletion.CUT_NOTE);
        assertThat(bodies).hasSize(1);
    }

    @Test
    void doesNotSendAnEmptyAssistantMessage_whenTheCutPartHasNoText() throws Exception {
        queued.add(reply("", "max_tokens"));

        assertThat(complete(clockAdvancing(Duration.ofSeconds(1)))).isEqualTo(ClaudeTextCompletion.CUT_NOTE.trim());
        assertThat(bodies).hasSize(1);
    }
}
