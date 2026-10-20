package com.vetos.modules.integration.efatura.infrastructure.adapter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.vetos.platform.observability.IntegrationOutcomes;
import java.time.Clock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceLineItem;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionOutcome;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRequest;
import com.vetos.platform.resilience.OutageFailurePredicate;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FaturaEntegratorEInvoiceGatewayAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, Clock.systemUTC());

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger getRequests = new AtomicInteger();
    private final AtomicInteger postRequests = new AtomicInteger();
    /** Doluysa GET istekleri sirayla bu durum kodlariyla yanitlanir (son eleman tekrarlanir). */
    private volatile int[] getStatusScript;

    private static RetryRegistry fastRetries() {
        return RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10))
            .retryOnException(new OutageFailurePredicate()).build());
    }

    private double failureCount(String name) {
        return breakers.circuitBreaker(name).getMetrics().getNumberOfFailedCalls();
    }

    private EInvoiceSubmissionRequest sampleRequest() {
        return aRequest();
    }

    private HttpServer stubServer;
    private String stubResponseBody;
    private int stubStatusCode = 200;
    private Headers capturedHeaders;
    private String capturedBody;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // POST /api/invoices (gonderim) ve GET /api/invoices/{id} (durum sorgusu)
        // ayni sabit yanit/durum koduyla tek bir context'te karsilanir -- testler
        // her senaryo icin stubResponseBody/stubStatusCode'u kendi ihtiyacina gore ayarlar.
        stubServer.createContext("/api/invoices", ex -> {
            capturedHeaders = ex.getRequestHeaders();
            capturedBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.incrementAndGet();
            int status = stubStatusCode;
            if ("GET".equals(ex.getRequestMethod())) {
                int n = getRequests.incrementAndGet();
                int[] script = getStatusScript;
                if (script != null) {
                    status = script[Math.min(n, script.length) - 1];
                }
            } else {
                postRequests.incrementAndGet();
            }
            byte[] payload = stubResponseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private EInvoiceSubmissionRequest aRequest() {
        return new EInvoiceSubmissionRequest(
            UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            "Ahmet Yilmaz", "Istiklal Cad. No:10", "Istanbul", "Kadikoy", "11111111111",
            new BigDecimal("120.00"), new BigDecimal("20.00"),
            List.of(new EInvoiceLineItem("line-1", "Muayene ucreti", 1, "C62", new BigDecimal("100.00"), new BigDecimal("20"))),
            "http://localhost:8080/api/v1/public/efatura/faturaentegrator/callback"
        );
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort() + "/api";
    }

    private FaturaEntegratorEInvoiceGatewayAdapter anAdapter(String apiKey) {
        return new FaturaEntegratorEInvoiceGatewayAdapter(apiKey, 753L, 1045L, new ObjectMapper(), baseUrl(), outcomes, breakers, fastRetries());
    }

    @Test
    void should_returnFailureOutcome_when_notConfigured() {
        EInvoiceGatewayPort adapter = new FaturaEntegratorEInvoiceGatewayAdapter("", 753L, 1045L, new ObjectMapper(), "http://unused", outcomes, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());

        EInvoiceSubmissionOutcome outcome = adapter.submit(aRequest());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("yapilandirilmamis");
    }

    @Test
    void should_returnPendingOutcome_when_providerAcceptsRequest() throws Exception {
        stubResponseBody = "{\"data\":{\"id\":123,\"workflow_status\":\"processing\",\"formal_status\":\"pending\","
            + "\"ettn\":null,\"serial_number\":null,\"pdf_url\":null}}";
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");

        EInvoiceSubmissionOutcome outcome = adapter.submit(aRequest());

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isFalse();
        assertThat(outcome.gibReference()).isEqualTo("123");
        assertThat(capturedHeaders.getFirst("Authorization")).isEqualTo("Bearer test-api-key");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sentBody = mapper.readTree(capturedBody);
        assertThat(sentBody.path("sale_channel_id").asLong()).isEqualTo(1045L);
        assertThat(sentBody.path("invoice_integration_id").asLong()).isEqualTo(753L);
        assertThat(sentBody.path("customer").path("tax_number").asText()).isEqualTo("11111111111");
        assertThat(sentBody.path("customer").path("type").asText()).isEqualTo("person");
        assertThat(sentBody.path("lines")).hasSize(1);
        assertThat(sentBody.path("lines").get(0).path("tax_rate").asText()).isEqualTo("20");
        assertThat(sentBody.path("lines").get(0).path("name").asText()).isEqualTo("Muayene ucreti");
    }

    @Test
    void should_returnFailureOutcome_when_providerRejectsRequest() {
        stubStatusCode = 422;
        stubResponseBody = "{\"message\":\"sale_channel_id gecersiz\"}";
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");

        EInvoiceSubmissionOutcome outcome = adapter.submit(aRequest());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("reddetti");
    }

    @Test
    void should_returnFailureOutcome_when_httpCallFails() {
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");
        stubServer.stop(0);

        EInvoiceSubmissionOutcome outcome = adapter.submit(aRequest());

        assertThat(outcome.success()).isFalse();
    }

    @Test
    void should_reportNotConfigured_when_apiKeyBlank() {
        FaturaEntegratorEInvoiceGatewayAdapter adapter =
            new FaturaEntegratorEInvoiceGatewayAdapter("", 753L, 1045L, new ObjectMapper(), "http://unused", outcomes, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());

        assertThat(adapter.isConfigured()).isFalse();
    }

    @Test
    void should_reportConfigured_when_allFieldsPresent() {
        FaturaEntegratorEInvoiceGatewayAdapter adapter =
            new FaturaEntegratorEInvoiceGatewayAdapter("key", 753L, 1045L, new ObjectMapper(), "http://unused", outcomes, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());

        assertThat(adapter.isConfigured()).isTrue();
    }

    @Test
    void should_returnFinalSuccess_when_fetchStatusFindsEttn() {
        stubResponseBody = "{\"data\":{\"id\":123,\"workflow_status\":\"finished\",\"formal_status\":\"success\","
            + "\"ettn\":\"ETTN-ABC-123\",\"errors\":[]}}";
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");

        EInvoiceSubmissionOutcome outcome = adapter.fetchStatus("123");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isTrue();
        assertThat(outcome.gibReference()).isEqualTo("ETTN-ABC-123");
    }

    @Test
    void should_recordFailure_when_fetchStatusReturnsEmptyBody() {
        stubResponseBody = "";

        anAdapter("test-api-key").fetchStatus("123");

        assertThat(calls("efatura", "failure")).isEqualTo(1);
        assertThat(calls("efatura", "success")).isZero();
    }

    @Test
    void should_returnFailure_when_fetchStatusFindsErrors() {
        stubResponseBody = "{\"data\":{\"id\":123,\"workflow_status\":\"finished\",\"formal_status\":\"failed\","
            + "\"ettn\":null,\"errors\":[{\"message\":\"GIB mukellef degil\"}]}}";
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");

        EInvoiceSubmissionOutcome outcome = adapter.fetchStatus("123");

        assertThat(outcome.success()).isFalse();
    }

    @Test
    void should_returnPending_when_fetchStatusStillProcessing() {
        stubResponseBody = "{\"data\":{\"id\":123,\"workflow_status\":\"processing\",\"formal_status\":\"pending\","
            + "\"ettn\":null,\"errors\":[]}}";
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");

        EInvoiceSubmissionOutcome outcome = adapter.fetchStatus("123");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isFalse();
    }

    @Test
    void should_recordSuccess_when_providerAccepts() {
        stubResponseBody = "{\"data\":{\"id\":123,\"workflow_status\":\"processing\"}}";

        anAdapter("test-api-key").submit(aRequest());

        assertThat(calls("efatura", "success")).isEqualTo(1);
        assertThat(calls("efatura", "failure")).isZero();
    }

    @Test
    void should_recordSuccess_when_providerRejectsWithValidationError() {
        stubStatusCode = 422;
        stubResponseBody = "{\"message\":\"sale_channel_id gecersiz\"}";

        anAdapter("test-api-key").submit(aRequest());

        assertThat(calls("efatura", "success")).isEqualTo(1);
    }

    @Test
    void should_recordFailure_when_providerReturns5xx() {
        stubStatusCode = 503;
        stubResponseBody = "{\"message\":\"bakim\"}";

        anAdapter("test-api-key").submit(aRequest());

        assertThat(calls("efatura", "failure")).isEqualTo(1);
    }

    @Test
    void should_recordFailure_when_providerUnreachable() {
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");
        stubServer.stop(0);

        adapter.submit(aRequest());

        assertThat(calls("efatura", "failure")).isEqualTo(1);
    }

    @Test
    void should_notRecord_when_notConfigured() {
        new FaturaEntegratorEInvoiceGatewayAdapter("", 753L, 1045L, new ObjectMapper(), "http://unused", outcomes, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults()).submit(aRequest());

        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }

    @Test
    void should_notRecordOutage_when_requestBuildingFails() {
        EInvoiceSubmissionRequest broken = new EInvoiceSubmissionRequest(
            UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            "Ahmet Yilmaz", "Istiklal Cad. No:10", "Istanbul", "Kadikoy", "11111111111",
            new BigDecimal("120.00"), new BigDecimal("20.00"), null, "http://localhost/callback"
        );

        EInvoiceSubmissionOutcome outcome = anAdapter("test-api-key").submit(broken);

        // Kendi istek-olusturma hatamiz saglayici kesintisi sayilmaz
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("faturaentegrator cagrisi basarisiz");
        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }

    @Test
    void should_skipHttp_and_returnTransientFailure_when_breakerOpenOnSubmit() {
        stubResponseBody = "{}";
        breakers.circuitBreaker("efatura").transitionToOpenState();
        int before = requests.get();
        double failuresBefore = failureCount("efatura");

        EInvoiceSubmissionOutcome outcome = anAdapter("test-api-key").submit(sampleRequest());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.finalResult()).isFalse();
        assertThat(requests.get()).isEqualTo(before);
        assertThat(failureCount("efatura")).isEqualTo(failuresBefore);
        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }

    @Test
    void should_returnPending_when_breakerOpen_onFetchStatus() {
        stubResponseBody = "{}";
        breakers.circuitBreaker("efatura").transitionToOpenState();

        EInvoiceSubmissionOutcome outcome = anAdapter("test-api-key").fetchStatus("ref-1");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isFalse();
        assertThat(outcome.gibReference()).isEqualTo("ref-1");
        assertThat(requests.get()).isZero();
        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }

    @Test
    void should_retryFetchStatus_onOutage() {
        getStatusScript = new int[] {503, 503, 200};
        stubResponseBody = "{\"data\":{\"ettn\":\"ETTN-1\"}}";

        EInvoiceSubmissionOutcome outcome = anAdapter("test-api-key").fetchStatus("ref-1");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isTrue();
        assertThat(getRequests.get()).isEqualTo(3);
        // deneme basina kayit: 2 kesinti + 1 basari
        assertThat(calls("efatura", "failure")).isEqualTo(2);
        assertThat(calls("efatura", "success")).isEqualTo(1);
    }

    @Test
    void should_returnPending_when_outageRetriesExhausted() {
        stubStatusCode = 503;
        stubResponseBody = "{\"message\":\"bakim\"}";

        EInvoiceSubmissionOutcome outcome = anAdapter("test-api-key").fetchStatus("ref-1");

        // Kesinti: fatura "basarisiz" isaretlenmemeli (cift fatura riski) -> pending
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isFalse();
        assertThat(outcome.gibReference()).isEqualTo("ref-1");
        assertThat(getRequests.get()).isEqualTo(3);
        assertThat(calls("efatura", "failure")).isEqualTo(3);
    }

    @Test
    void should_returnFailure_on_clientError_withoutRetry() {
        stubStatusCode = 404;
        stubResponseBody = "{\"message\":\"yok\"}";

        EInvoiceSubmissionOutcome outcome = anAdapter("test-api-key").fetchStatus("ref-1");

        assertThat(outcome.success()).isFalse();
        assertThat(getRequests.get()).isEqualTo(1);
    }

    @Test
    void should_notRetrySubmit() {
        stubStatusCode = 503;
        stubResponseBody = "{\"message\":\"bakim\"}";

        anAdapter("test-api-key").submit(sampleRequest());

        assertThat(postRequests.get()).isEqualTo(1);
    }
}
