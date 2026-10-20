package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canli docker-compose Postgres gerekir. management.server.port=0: yerelde :8081'de calisan
 * backend ile cakismasin diye rastgele yonetim portu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ExtendWith(OutputCaptureExtension.class)
// @SpringBootTest varsayilan olarak metrik disa aktarimini (Prometheus) kapatir; scrape testi icin acilir.
@AutoConfigureObservability
class ObservabilityWiringTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private IntegrationOutcomes outcomes;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_serveReadinessAndExternal_onManagementPort_withoutDetails() throws Exception {
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
        HttpResponse<String> external = get(managementPort, "/actuator/health/external");
        HttpResponse<String> liveness = get(managementPort, "/actuator/health/liveness");

        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(external.statusCode()).isEqualTo(200);
        assertThat(external.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(liveness.statusCode()).isEqualTo(200);
    }

    @Test
    void should_reportExternalDown_when_integrationFailsRepeatedly_andRecover() throws Exception {
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);

        HttpResponse<String> down = get(managementPort, "/actuator/health/external");
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");

        outcomes.record(IntegrationOutcomes.PAYMENT, true); // paylasilan context'i temiz birak
        HttpResponse<String> up = get(managementPort, "/actuator/health/external");

        assertThat(down.statusCode()).isEqualTo(503);
        assertThat(down.body()).contains("\"status\":\"DOWN\"");
        assertThat(readiness.statusCode()).isEqualTo(200); // dis servis readiness'i etkilemez
        assertThat(up.statusCode()).isEqualTo(200);
    }

    @Test
    void should_exposeVetlyMetrics_onManagementPortOnly() throws Exception {
        outcomes.record(IntegrationOutcomes.TARBIL, true);

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
        HttpResponse<String> onMainPort = get(serverPort, "/actuator/prometheus");

        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("vetly_integration_calls_total").contains("application=\"vetly\"");
        assertThat(onMainPort.statusCode()).isEqualTo(404);
    }

    @Test
    void should_maskElevenDigitNumbers_inPlainTextConsoleLog(CapturedOutput output) {
        LoggerFactory.getLogger(ObservabilityWiringTest.class).info("TCKN kontrol 12345678901 bitti");

        assertThat(output.getOut()).contains("TCKN kontrol 123******01 bitti");
        assertThat(output.getOut()).doesNotContain("12345678901");
        // LOG_FORMAT bos -> duz metin (JSON degil) ve requestId/tenantId alani duruyor
        assertThat(output.getOut()).contains("[etc./sistem]");
    }
}
