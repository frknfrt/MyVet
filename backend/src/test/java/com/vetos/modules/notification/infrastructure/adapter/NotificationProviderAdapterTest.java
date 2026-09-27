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

class NotificationProviderAdapterTest {

    private static final String META_API_VERSION = "v99.0";
    private static final String META_PHONE_NUMBER_ID = "1234567890";
    private static final String META_MESSAGES_PATH = "/" + META_API_VERSION + "/" + META_PHONE_NUMBER_ID + "/messages";
    private static final String ILETI_MERKEZI_PATH = "/v1/send-sms/json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer stubServer;
    private final Map<String, String> capturedBodies = new ConcurrentHashMap<>();
    private final Map<String, String> capturedAuthHeaders = new ConcurrentHashMap<>();
    private volatile String iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":200,\"message\":\"Islem basarili\"},\"order\":{\"id\":\"1\"}}}";
    private volatile String metaStubResponse = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.test\"}]}";

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext(ILETI_MERKEZI_PATH, ex -> handle(ex, iletiMerkeziStubResponse));
        stubServer.createContext(META_MESSAGES_PATH, ex -> handle(ex, metaStubResponse));
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

    private NotificationProviderAdapter adapter(String metaAccessToken, String iletiMerkeziApiKey, String iletiMerkeziHash) {
        return templatedAdapter(metaAccessToken, "", iletiMerkeziApiKey, iletiMerkeziHash);
    }

    private NotificationProviderAdapter templatedAdapter(
        String metaAccessToken, String metaTemplateName, String iletiMerkeziApiKey, String iletiMerkeziHash
    ) {
        return new NotificationProviderAdapter(
            metaAccessToken, META_PHONE_NUMBER_ID, metaTemplateName, "en_US", META_API_VERSION,
            iletiMerkeziApiKey, iletiMerkeziHash, "vetly",
            stubBaseUrl(), stubBaseUrl() + ILETI_MERKEZI_PATH
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentMetaBody() throws Exception {
        return objectMapper.readValue(capturedBodies.get(META_MESSAGES_PATH), new TypeReference<>() {});
    }

    // ------------------------------------------------------ simule mod

    @Test
    void should_notBeConfigured_when_allCredentialsAreBlank() {
        assertThat(adapter("", "", "").isConfigured()).isFalse();
    }

    @Test
    void should_sendSimulated_when_smsRequestedAndNoProviderConfigured() {
        // sendSimulated has a built-in random failure rate -- assert only that
        // no real HTTP call was made, not the specific success/failure outcome.
        adapter("", "", "").send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(capturedBodies).isEmpty();
    }

    // ------------------------------------------------------ kimlik bilgisi kombinasyonu

    @Test
    void should_beConfigured_when_onlyIletiMerkeziCredentialsAreSet() {
        assertThat(adapter("", "test-key", "test-hash").isConfigured()).isTrue();
    }

    @Test
    void should_beConfigured_when_onlyMetaCredentialsAreSet() {
        assertThat(adapter("test-token", "", "").isConfigured()).isTrue();
    }

    // ------------------------------------------------------ kanal-bazli durum (Ayarlar > SMS/WhatsApp ekrani icin)

    @Test
    void should_reportPerChannelStatus_when_onlyIletiMerkeziCredentialsAreSet() {
        var adapter = adapter("", "test-key", "test-hash");
        assertThat(adapter.isSmsConfigured()).isTrue();
        assertThat(adapter.isWhatsappConfigured()).isFalse();
    }

    @Test
    void should_reportPerChannelStatus_when_onlyMetaCredentialsAreSet() {
        var adapter = adapter("test-token", "", "");
        assertThat(adapter.isSmsConfigured()).isFalse();
        assertThat(adapter.isWhatsappConfigured()).isTrue();
    }

    @Test
    void should_reportBothChannelsConfigured_when_bothCredentialsAreSet() {
        var adapter = adapter("test-token", "test-key", "test-hash");
        assertThat(adapter.isSmsConfigured()).isTrue();
        assertThat(adapter.isWhatsappConfigured()).isTrue();
    }

    // ------------------------------------------------------ gercek Ileti Merkezi cagrisi (yerel sahte sunucu)

    @Test
    void should_callIletiMerkeziAndReturnSuccess_when_smsConfiguredAndGatewayAccepts() {
        iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":200,\"message\":\"Islem basarili\"},\"order\":{\"id\":\"555\"}}}";

        NotificationSendOutcome outcome = adapter("", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Randevunuz yarin saat 14:00"));

        assertThat(outcome.success()).isTrue();
        assertThat(capturedBodies).containsKey(ILETI_MERKEZI_PATH);
    }

    @Test
    void should_callIletiMerkeziAndReturnFailure_when_gatewayRejects() {
        iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":401,\"message\":\"Kimlik dogrulama basarisiz\"}}}";

        NotificationSendOutcome outcome = adapter("", "bad-key", "bad-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("401");
    }

    @Test
    void should_sendKeyHashSenderAndFormattedPhone_when_callingIletiMerkezi() throws Exception {
        adapter("", "test-key", "test-hash")
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
    void should_routeWhatsAppToMetaOnly_when_bothProvidersConfigured() {
        NotificationSendOutcome outcome = adapter("test-token", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Merhaba"));

        assertThat(outcome.success()).isTrue();
        assertThat(capturedBodies).containsKey(META_MESSAGES_PATH);
        assertThat(capturedBodies).doesNotContainKey(ILETI_MERKEZI_PATH);
    }

    @Test
    void should_routeSmsToIletiMerkeziOnly_when_bothProvidersConfigured() {
        NotificationSendOutcome outcome = adapter("test-token", "test-key", "test-hash")
            .send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(outcome.success()).isTrue();
        assertThat(capturedBodies).containsKey(ILETI_MERKEZI_PATH);
        assertThat(capturedBodies).doesNotContainKey(META_MESSAGES_PATH);
    }

    // ------------------------------------------------------ gercek Meta Graph API cagrisi (yerel sahte sunucu)

    @Test
    void should_sendBearerAuthHeader_when_callingMeta() {
        adapter("test-access-token", "", "")
            .send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Merhaba"));

        assertThat(capturedAuthHeaders.get(META_MESSAGES_PATH)).isEqualTo("Bearer test-access-token");
    }

    @Test
    void should_sendTextTypeWithNormalizedPhone_when_noTemplateConfigured() throws Exception {
        adapter("test-token", "", "")
            .send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Vetly Klinik: Randevunuz yarin"));

        Map<String, Object> sent = sentMetaBody();
        assertThat(sent.get("messaging_product")).isEqualTo("whatsapp");
        assertThat(sent.get("to")).isEqualTo("905551234567");
        assertThat(sent.get("type")).isEqualTo("text");
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) sent.get("text");
        assertThat(text.get("body")).isEqualTo("Vetly Klinik: Randevunuz yarin");
        assertThat(sent).doesNotContainKey("template");
    }

    @Test
    void should_sendTemplateComponentsInsteadOfText_when_templateNameConfigured() throws Exception {
        templatedAdapter("test-token", "hello_world", "", "")
            .send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Vetly Klinik: Randevunuz yarin"));

        Map<String, Object> sent = sentMetaBody();
        assertThat(sent.get("type")).isEqualTo("template");
        assertThat(sent).doesNotContainKey("text");
        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) sent.get("template");
        assertThat(template.get("name")).isEqualTo("hello_world");
        @SuppressWarnings("unchecked")
        Map<String, Object> language = (Map<String, Object>) template.get("language");
        assertThat(language.get("code")).isEqualTo("en_US");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");
        assertThat(components).hasSize(1);
        assertThat(components.get(0).get("type")).isEqualTo("body");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) components.get(0).get("parameters");
        assertThat(parameters).hasSize(1);
        assertThat(parameters.get(0).get("type")).isEqualTo("text");
        assertThat(parameters.get(0).get("text")).isEqualTo("Vetly Klinik: Randevunuz yarin");
    }
}
