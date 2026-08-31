package com.vetos.modules.notification.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationSendOutcome;
import com.vetos.modules.notification.domain.NotificationSendRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioNotificationAdapterTest {

    private static final String TWILIO_PATH = "/twilio/Messages.json";
    private static final String ILETI_MERKEZI_PATH = "/v1/send-sms/json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer stubServer;
    private final Map<String, String> capturedBodies = new ConcurrentHashMap<>();
    private final Map<String, String> capturedAuthHeaders = new ConcurrentHashMap<>();
    private volatile String iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":200,\"message\":\"Islem basarili\"},\"order\":{\"id\":\"1\"}}}";
    private volatile String twilioStubResponse = "{}";

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext(ILETI_MERKEZI_PATH, ex -> handle(ex, iletiMerkeziStubResponse));
        stubServer.createContext(TWILIO_PATH, ex -> handle(ex, twilioStubResponse));
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private void handle(HttpExchange exchange, String responseBody) throws IOException {
        capturedBodies.put(exchange.getRequestURI().getPath(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            capturedAuthHeaders.put(exchange.getRequestURI().getPath(), authHeader);
        }
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private String stubBaseUrl() {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort();
    }

    private TwilioNotificationAdapter adapter(
        String accountSid, String authToken, String iletiMerkeziApiKey, String iletiMerkeziHash
    ) {
        return adapter(accountSid, "", authToken, iletiMerkeziApiKey, iletiMerkeziHash);
    }

    private TwilioNotificationAdapter adapter(
        String accountSid, String authSid, String authToken, String iletiMerkeziApiKey, String iletiMerkeziHash
    ) {
        return new TwilioNotificationAdapter(
            accountSid, authSid, authToken, "whatsapp:+14155238886",
            iletiMerkeziApiKey, iletiMerkeziHash, "vetly",
            stubBaseUrl() + TWILIO_PATH, stubBaseUrl() + ILETI_MERKEZI_PATH
        );
    }

    // ------------------------------------------------------ simule mod

    @Test
    void should_notBeConfigured_when_allCredentialsAreBlank() {
        assertThat(adapter("", "", "", "").isConfigured()).isFalse();
    }

    @Test
    void should_sendSimulated_when_smsRequestedAndNoProviderConfigured() {
        // sendSimulated has a built-in random failure rate -- assert only that
        // no real HTTP call was made, not the specific success/failure outcome.
        adapter("", "", "", "").send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(capturedBodies).isEmpty();
    }

    // ------------------------------------------------------ kimlik bilgisi kombinasyonu

    @Test
    void should_beConfigured_when_onlyIletiMerkeziCredentialsAreSet() {
        assertThat(adapter("", "", "test-key", "test-hash").isConfigured()).isTrue();
    }

    @Test
    void should_beConfigured_when_onlyTwilioCredentialsAreSet() {
        assertThat(adapter("sid", "token", "", "").isConfigured()).isTrue();
    }

    // ------------------------------------------------------ gercek Ileti Merkezi cagrisi (yerel sahte sunucu)

    @Test
    void should_callIletiMerkeziAndReturnSuccess_when_smsConfiguredAndGatewayAccepts() {
        iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":200,\"message\":\"Islem basarili\"},\"order\":{\"id\":\"555\"}}}";

        NotificationSendOutcome outcome = adapter("", "", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Randevunuz yarin saat 14:00"));

        assertThat(outcome.success()).isTrue();
        assertThat(capturedBodies).containsKey(ILETI_MERKEZI_PATH);
    }

    @Test
    void should_callIletiMerkeziAndReturnFailure_when_gatewayRejects() {
        iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":401,\"message\":\"Kimlik dogrulama basarisiz\"}}}";

        NotificationSendOutcome outcome = adapter("", "", "bad-key", "bad-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("401");
    }

    @Test
    void should_sendKeyHashSenderAndFormattedPhone_when_callingIletiMerkezi() throws Exception {
        adapter("", "", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Test mesaji"));

        Map<String, Object> sent = objectMapper.readValue(capturedBodies.get(ILETI_MERKEZI_PATH), new TypeReference<>() {});
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
        assertThat(message.get("text")).isEqualTo("Test mesaji");
        assertThat(receipents.get("number")).isEqualTo(List.of("+905551234567"));
    }

    // ------------------------------------------------------ kanal yonlendirme birbirine karismiyor

    @Test
    void should_routeWhatsAppToTwilioOnly_when_bothProvidersConfigured() {
        NotificationSendOutcome outcome = adapter("test-sid", "test-token", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Merhaba"));

        assertThat(outcome.success()).isTrue();
        assertThat(capturedBodies).containsKey(TWILIO_PATH);
        assertThat(capturedBodies).doesNotContainKey(ILETI_MERKEZI_PATH);
    }

    @Test
    void should_useAuthSidNotAccountSid_forBasicAuth_when_apiKeyCredentialsAreUsed() throws Exception {
        // Twilio API Key modu: Basic Auth kullanici adi (SK... API Key SID) ile
        // URL yolundaki gercek Account SID (AC...) FARKLI degerlerdir.
        TwilioNotificationAdapter apiKeyAdapter = adapter("AC-real-account-sid", "SK-api-key-sid", "api-key-secret", "", "");

        apiKeyAdapter.send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Merhaba"));

        String expectedAuth = "Basic " + java.util.Base64.getEncoder()
            .encodeToString("SK-api-key-sid:api-key-secret".getBytes(StandardCharsets.UTF_8));
        assertThat(capturedAuthHeaders.get(TWILIO_PATH)).isEqualTo(expectedAuth);
    }

    @Test
    void should_routeSmsToIletiMerkeziOnly_when_bothProvidersConfigured() {
        NotificationSendOutcome outcome = adapter("test-sid", "test-token", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(outcome.success()).isTrue();
        assertThat(capturedBodies).containsKey(ILETI_MERKEZI_PATH);
        assertThat(capturedBodies).doesNotContainKey(TWILIO_PATH);
    }
}
