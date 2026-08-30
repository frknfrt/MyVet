package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class IletiMerkeziPlatformBillingSmsAdapterTest {

    private static final String SEND_PATH = "/v1/send-sms/json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlatformInvoice invoice = PlatformInvoice.issue(
        UUID.randomUUID(), "PRO", new BigDecimal("500.00"),
        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)
    );

    private HttpServer stubServer;
    private final Map<String, String> capturedBodies = new ConcurrentHashMap<>();
    private volatile String stubResponse = "{\"response\":{\"status\":{\"code\":200,\"message\":\"Islem basarili\"},\"order\":{\"id\":\"1\"}}}";

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext(SEND_PATH, this::handle);
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedBodies.put(exchange.getRequestURI().getPath(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] payload = stubResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private String stubBaseUrl() {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort();
    }

    private IletiMerkeziPlatformBillingSmsAdapter configuredAdapter() {
        return new IletiMerkeziPlatformBillingSmsAdapter("test-key", "test-hash", "vetly", stubBaseUrl() + SEND_PATH);
    }

    private IletiMerkeziPlatformBillingSmsAdapter unconfiguredAdapter() {
        return new IletiMerkeziPlatformBillingSmsAdapter("", "", "vetly", stubBaseUrl() + SEND_PATH);
    }

    // ------------------------------------------------------ simule mod: kimlik bilgisi bos

    @Test
    void should_notCallGateway_when_credentialsAreBlank() {
        unconfiguredAdapter().sendInvoiceIssued(invoice, "Test Klinik", "05551234567");

        assertThat(capturedBodies).isEmpty();
    }

    @Test
    void should_notCallGateway_forDueSoonAndSuspended_when_credentialsAreBlank() {
        unconfiguredAdapter().sendInvoiceDueSoon(invoice, "Test Klinik", "05551234567");
        unconfiguredAdapter().sendTenantSuspended("Test Klinik", "05551234567");

        assertThat(capturedBodies).isEmpty();
    }

    // ------------------------------------------------------ gercek mod: yerel sahte sunucu

    @Test
    void should_sendInvoiceIssuedSms_when_configured() throws Exception {
        configuredAdapter().sendInvoiceIssued(invoice, "Test Klinik", "05551234567");

        Map<String, Object> sent = objectMapper.readValue(capturedBodies.get(SEND_PATH), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) sent.get("request");
        @SuppressWarnings("unchecked")
        Map<String, Object> authentication = (Map<String, Object>) request.get("authentication");
        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) request.get("order");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) order.get("message");
        @SuppressWarnings("unchecked")
        Map<String, Object> receipents = (Map<String, Object>) message.get("receipents");

        assertThat(authentication.get("key")).isEqualTo("test-key");
        assertThat(authentication.get("hash")).isEqualTo("test-hash");
        assertThat(order.get("sender")).isEqualTo("vetly");
        assertThat(order.get("iys")).isEqualTo("0");
        assertThat(receipents.get("number")).isEqualTo(List.of("+905551234567"));
        assertThat((String) message.get("text")).contains("Test Klinik").contains("500").contains("08.08.2026");
    }

    @Test
    void should_sendInvoiceDueSoonSms_when_configured() throws Exception {
        configuredAdapter().sendInvoiceDueSoon(invoice, "Test Klinik", "05551234567");

        Map<String, Object> sent = objectMapper.readValue(capturedBodies.get(SEND_PATH), new TypeReference<>() {});
        String text = extractText(sent);

        assertThat(text).contains("Test Klinik").contains("500").contains("08.08.2026");
    }

    @Test
    void should_sendTenantSuspendedSms_when_configured() throws Exception {
        configuredAdapter().sendTenantSuspended("Test Klinik", "05551234567");

        Map<String, Object> sent = objectMapper.readValue(capturedBodies.get(SEND_PATH), new TypeReference<>() {});
        String text = extractText(sent);

        assertThat(text).contains("Test Klinik");
    }

    @Test
    void should_notThrow_when_gatewayReturnsError() {
        stubResponse = "{\"response\":{\"status\":{\"code\":401,\"message\":\"Kimlik dogrulama basarisiz\"}}}";

        // Bir platform-billing zamanlanmis isi (RemindDueSoonInvoicesUseCase vb.)
        // birden fazla tenant'i tek tek isler -- bir SMS gonderim hatasi tum
        // batch'i durdurmamali, bu yuzden port hicbir istisna firlatmaz.
        configuredAdapter().sendInvoiceIssued(invoice, "Test Klinik", "05551234567");

        assertThat(capturedBodies).containsKey(SEND_PATH);
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> sent) {
        Map<String, Object> request = (Map<String, Object>) sent.get("request");
        Map<String, Object> order = (Map<String, Object>) request.get("order");
        Map<String, Object> message = (Map<String, Object>) order.get("message");
        return (String) message.get("text");
    }
}
