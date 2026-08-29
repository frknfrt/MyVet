package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import com.vetos.modules.platformadmin.domain.exception.PaymentGatewayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IyzicoPaymentGatewayAdapterTest {

    private static final String INITIALIZE_PATH = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
    private static final String RETRIEVE_PATH = "/payment/iyzipos/checkoutform/auth/ecom/detail";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Simule mod: kimlik bilgileri bos. */
    private final IyzicoPaymentGatewayAdapter adapter =
        new IyzicoPaymentGatewayAdapter("", "", "https://sandbox-api.iyzipay.com", "http://localhost:8080");

    /** Kimlik bilgileri tanimli, base-url erisilemez: gercek mod yolu deterministik baglanti hatasi verir. */
    private final IyzicoPaymentGatewayAdapter unreachableConfiguredAdapter =
        new IyzicoPaymentGatewayAdapter("test-api-key", "test-secret-key", "http://127.0.0.1:1", "http://localhost:8080");

    /** Kimlik bilgileri tanimli ve asagidaki yerel sahte iyzico sunucusuna baglanan adapter. */
    private IyzicoPaymentGatewayAdapter stubbedConfiguredAdapter;

    private HttpServer stubServer;
    private final Map<String, String> capturedBodies = new ConcurrentHashMap<>();
    private volatile String stubResponseBody = "{}";

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext(INITIALIZE_PATH, this::handle);
        stubServer.createContext(RETRIEVE_PATH, this::handle);
        stubServer.start();

        stubbedConfiguredAdapter = new IyzicoPaymentGatewayAdapter(
            "test-api-key", "test-secret-key",
            "http://127.0.0.1:" + stubServer.getAddress().getPort(), "http://localhost:8080");
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedBodies.put(exchange.getRequestURI().getPath(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] payload = stubResponseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    // ---------------------------------------------------------------- simule mod

    @Test
    void should_notBeConfigured_when_credentialsAreBlank() {
        assertThat(adapter.isConfigured()).isFalse();
    }

    @Test
    void should_returnSimulatedCheckoutFormUrl_when_notConfigured() {
        String conversationId = UUID.randomUUID().toString();

        CheckoutSession session = adapter.initializeCheckout(conversationId, new BigDecimal("500.00"), "Test Klinik", "test@example.com");

        assertThat(session.checkoutFormUrl())
            .isEqualTo("http://localhost:8080/api/v1/public/payments/iyzico/callback?token=SIMULATED-" + conversationId);
        assertThat(session.token()).isEqualTo("SIMULATED-" + conversationId);
    }

    @Test
    void should_returnSuccessfulResult_when_retrievingSimulatedToken() {
        String conversationId = UUID.randomUUID().toString();

        CheckoutResult result = adapter.retrieveCheckoutResult("SIMULATED-" + conversationId);

        assertThat(result.success()).isTrue();
        assertThat(result.conversationId()).isEqualTo(conversationId);
        assertThat(result.paymentId()).isNotBlank();
    }

    // ------------------------------------------------- BULGU 1: simulasyon bypass'i

    /**
     * GUVENLIK REGRESYON TESTI (Bulgu 1): kimlik bilgileri tanimliyken "SIMULATED-"
     * onekli token simulasyon kisayolunu ACMAMALI -- aksi halde public callback ucuna
     * "?token=SIMULATED-&lt;fatura-id&gt;" gonderen herkes o faturayi bedava odenmis
     * isaretletebilirdi. Erisilemez base-url ile: gercek mod HTTP cagrisi denenir ve
     * baglanti reddi RestClientException (ResourceAccessException) olarak disari cikar --
     * gercek mod catch blogu yalnizca RestClientResponseException yakalar.
     */
    @Test
    void should_notShortCircuitToSimulatedSuccess_when_configuredAndTokenHasSimulatedPrefix() {
        assertThat(unreachableConfiguredAdapter.isConfigured()).isTrue();

        assertThatThrownBy(() -> unreachableConfiguredAdapter.retrieveCheckoutResult("SIMULATED-" + UUID.randomUUID()))
            .isInstanceOf(RestClientException.class);
    }

    /**
     * Ayni regresyon, cevap veren bir gateway ile: "SIMULATED-" onekli token gercekten
     * tel uzerinden gateway'e gider ve gateway'in reddi (HTTP 200 + status=failure)
     * success=false olarak yansir. Simule "odendi" sonucu asla uretilmez.
     */
    @Test
    void should_callRealGatewayAndReturnFailure_when_configuredAndTokenHasSimulatedPrefix() {
        stubResponseBody = "{\"status\":\"failure\",\"errorCode\":\"11\",\"errorMessage\":\"Gecersiz istek\"}";
        String simulatedToken = "SIMULATED-" + UUID.randomUUID();

        CheckoutResult result = stubbedConfiguredAdapter.retrieveCheckoutResult(simulatedToken);

        assertThat(result.success()).isFalse();
        assertThat(result.paymentId()).isNull();
        // token gercekten gateway'e gonderildi -- kisayol devrede degil
        assertThat(capturedBodies.get(RETRIEVE_PATH)).contains(simulatedToken);
    }

    /** initializeCheckout de yapilandirilmisken simulasyon URL'i uretmemeli. */
    @Test
    void should_notReturnSimulatedCheckoutFormUrl_when_configured() {
        assertThatThrownBy(() -> unreachableConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com"))
            .isInstanceOf(RestClientException.class);
    }

    // ------------------------------------------- BULGU 2: HTTP 200 + status=failure

    @Test
    void should_throwPaymentGatewayException_when_initializeReturnsFailureStatusWithHttp200() {
        stubResponseBody = "{\"status\":\"failure\",\"errorCode\":\"5007\",\"errorMessage\":\"Tutar gecersiz\"}";

        assertThatThrownBy(() -> stubbedConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com"))
            .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void should_throwPaymentGatewayException_when_initializeReturnsEmptyBody() {
        stubResponseBody = "";

        assertThatThrownBy(() -> stubbedConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com"))
            .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void should_returnCheckoutSession_when_initializeReturnsSuccessStatus() {
        stubResponseBody = "{\"status\":\"success\",\"paymentPageUrl\":\"https://odeme.example/x\",\"token\":\"tok-1\"}";

        CheckoutSession session = stubbedConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com");

        assertThat(session.checkoutFormUrl()).isEqualTo("https://odeme.example/x");
        assertThat(session.token()).isEqualTo("tok-1");
    }

    // ----------------------------------------------- BULGU 5: JSON kacislama

    /**
     * BULGU 5: initialize govdesi Jackson ile serilestirilir; kotu niyetli bir klinik
     * adi ("}, "price":"0.01" ...) artik ust seviye alan enjekte edemez. Govde tel
     * uzerinden yakalanip yeniden ayristirilarak dogrulanir.
     */
    @Test
    void should_escapeQuotesInOutgoingBody_when_buyerNameContainsInjectionAttempt() throws Exception {
        stubResponseBody = "{\"status\":\"success\",\"paymentPageUrl\":\"https://odeme.example/x\",\"token\":\"tok-1\"}";
        String maliciousName = "Kotu Klinik\", \"price\": \"0.01\", \"x\": \"";

        stubbedConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), maliciousName, "test@example.com");

        Map<String, Object> sent = objectMapper.readValue(capturedBodies.get(INITIALIZE_PATH), new TypeReference<>() {});
        assertThat(sent.get("price")).isEqualTo("500.00");
        assertThat(sent.get("paidPrice")).isEqualTo("500.00");
        assertThat(sent).doesNotContainKey("x");
        @SuppressWarnings("unchecked")
        Map<String, Object> buyer = (Map<String, Object>) sent.get("buyer");
        assertThat(buyer.get("name")).isEqualTo(maliciousName);
    }

    /**
     * BULGU 5: retrieve govdesindeki token public ve saldirgan kontrolunde --
     * icindeki tirnak govdeyi bozmamali, sadece kacislanmali.
     */
    @Test
    void should_escapeQuotesInOutgoingBody_when_tokenContainsQuotes() throws Exception {
        stubResponseBody = "{\"status\":\"failure\"}";
        String maliciousToken = "abc\", \"locale\": \"en\", \"y\": \"";

        stubbedConfiguredAdapter.retrieveCheckoutResult(maliciousToken);

        Map<String, Object> sent = objectMapper.readValue(capturedBodies.get(RETRIEVE_PATH), new TypeReference<>() {});
        assertThat(sent.get("locale")).isEqualTo("tr");
        assertThat(sent.get("token")).isEqualTo(maliciousToken);
        assertThat(sent).doesNotContainKey("y");
    }

    /** toJson dogrudan: govde her zaman gecerli JSON uretmeli. */
    @Test
    void should_produceParseableJson_when_serializingInitializeBody() throws Exception {
        Map<String, Object> body = IyzicoPaymentGatewayAdapter.initializeRequestBody(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Klinik \"A\"", "a@b.com", "http://localhost:8080/cb");

        Map<String, Object> parsed = objectMapper.readValue(adapter.toJson(body), new TypeReference<>() {});

        assertThat(parsed.get("currency")).isEqualTo("TRY");
        assertThat(parsed.get("callbackUrl")).isEqualTo("http://localhost:8080/cb");
        assertThat(parsed.get("enabledInstallments")).isEqualTo(java.util.List.of(1));
    }

    /** Ayni serilestirme kullanilmayan alan birakmamali: LinkedHashMap sirasi korunur. */
    @Test
    void should_keepRetrieveBodyMinimal_when_serializing() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locale", "tr");
        body.put("token", "tok");

        assertThat(adapter.toJson(body)).isEqualTo("{\"locale\":\"tr\",\"token\":\"tok\"}");
    }
}
