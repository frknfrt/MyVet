package com.vetos.platform.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundHttpTest {

    private HttpServer stubServer;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stubServer.createContext("/ok", exchange -> {
            byte[] payload = "merhaba".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort() + path;
    }

    @Test
    void should_failWithinReadTimeout_when_serverNeverResponds() {
        RestClient client = OutboundHttp.restClient(Duration.ofSeconds(1), Duration.ofMillis(300));

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.get().uri(url("/slow")).retrieve().body(String.class))
            .isInstanceOf(ResourceAccessException.class);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMillis).isLessThan(2_000);
    }

    @Test
    void should_returnBody_when_serverRespondsInTime() {
        RestClient client = OutboundHttp.restClient(Duration.ofSeconds(1), Duration.ofSeconds(2));

        String body = client.get().uri(url("/ok")).retrieve().body(String.class);

        assertThat(body).isEqualTo("merhaba");
    }
}
