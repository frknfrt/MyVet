package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPaymentCallbackControllerTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Mock private HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private PublicPaymentCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPaymentCallbackController(handlePaymentCallbackUseCase, paymentGatewayPort, FRONTEND);
    }

    private static String location(ResponseEntity<Void> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return String.valueOf(response.getHeaders().getLocation());
    }

    /**
     * BULGU 1 (ikinci savunma katmani): gateway gercekten yapilandirilmissa GET
     * rotasi token'a hic dokunmadan reddedilmeli -- gercek iyzico her zaman POST eder,
     * GET yalnizca yerel simule mod icin var.
     */
    @Test
    void should_rejectGetWithoutTouchingToken_when_gatewayIsConfigured() {
        when(paymentGatewayPort.isConfigured()).thenReturn(true);

        ResponseEntity<Void> response = controller.handleGet("SIMULATED-" + UUID.randomUUID());

        assertThat(location(response)).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handlePaymentCallbackUseCase);
    }

    @Test
    void should_processGet_when_gatewayIsNotConfigured() {
        when(paymentGatewayPort.isConfigured()).thenReturn(false);
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        ResponseEntity<Void> response = controller.handleGet("SIMULATED-" + UUID.randomUUID());

        assertThat(location(response)).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verify(handlePaymentCallbackUseCase).execute(anyString(), any(LocalDate.class));
    }

    @Test
    void should_redirectToSuccess_when_postCallbackSucceeds() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verify(paymentGatewayPort, never()).isConfigured();
    }

    @Test
    void should_redirectToFailure_when_postCallbackReturnsFalse() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
    }

    /**
     * BULGU 3: use case istisna firlatirsa kullaniciya ham JSON hata govdesi
     * gosterilmemeli -- her durumda frontend'e yonlendirilmeli.
     */
    @Test
    void should_redirectToFailure_when_useCaseThrowsDomainException() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
    }

    @Test
    void should_redirectToFailure_when_useCaseThrowsUnexpectedRuntimeException() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new IllegalArgumentException("Invalid UUID string"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
    }
}
