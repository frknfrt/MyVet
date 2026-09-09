package com.vetos.modules.integration.efatura.api;

import com.vetos.modules.integration.efatura.api.dto.FaturaEntegratorCallbackRequest;
import com.vetos.modules.integration.efatura.application.ApplyEInvoiceCallbackUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaturaEntegratorCallbackControllerTest {

    private static final String API_KEY = "test-api-key";

    @Mock private ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    private FaturaEntegratorCallbackController controller;

    private FaturaEntegratorCallbackRequest validRequest() {
        long invoiceId = 123L;
        long teamId = 45L;
        String time = "2026-07-02 14:30:15";
        String hash = hmacSha512Hex(invoiceId + String.valueOf(teamId) + time, API_KEY);
        return new FaturaEntegratorCallbackRequest(invoiceId, teamId, time, hash);
    }

    private static String hmacSha512Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void should_return200AndApplyCallback_when_signatureValid() {
        controller = new FaturaEntegratorCallbackController(applyEInvoiceCallbackUseCase, API_KEY);
        FaturaEntegratorCallbackRequest request = validRequest();

        ResponseEntity<Void> response = controller.handleCallback(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applyEInvoiceCallbackUseCase).execute("123");
    }

    @Test
    void should_return401AndNotApplyCallback_when_signatureInvalid() {
        controller = new FaturaEntegratorCallbackController(applyEInvoiceCallbackUseCase, API_KEY);
        FaturaEntegratorCallbackRequest request = new FaturaEntegratorCallbackRequest(123L, 45L, "2026-07-02 14:30:15", "yanlis-imza");

        ResponseEntity<Void> response = controller.handleCallback(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(applyEInvoiceCallbackUseCase);
    }

    @Test
    void should_return401_when_apiKeyNotConfigured() {
        controller = new FaturaEntegratorCallbackController(applyEInvoiceCallbackUseCase, "");
        FaturaEntegratorCallbackRequest request = validRequest();

        ResponseEntity<Void> response = controller.handleCallback(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(applyEInvoiceCallbackUseCase);
    }
}
