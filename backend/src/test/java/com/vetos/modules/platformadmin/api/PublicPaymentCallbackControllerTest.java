package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.application.HandleSignupPaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
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
    private static final String VETLY_SITE = "http://localhost:5175";

    @Mock private HandlePaymentCallbackUseCase handlePaymentCallbackUseCase;
    @Mock private HandleSignupPaymentCallbackUseCase handleSignupPaymentCallbackUseCase;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private PublicPaymentCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPaymentCallbackController(
            handlePaymentCallbackUseCase, handleSignupPaymentCallbackUseCase, paymentGatewayPort, FRONTEND, VETLY_SITE
        );
    }

    private static String location(ResponseEntity<Void> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return String.valueOf(response.getHeaders().getLocation());
    }

    @Test
    void should_rejectGetWithoutTouchingToken_when_gatewayIsConfigured() {
        when(paymentGatewayPort.isConfigured()).thenReturn(true);

        ResponseEntity<Void> response = controller.handleGet("SIMULATED-" + UUID.randomUUID());

        assertThat(location(response)).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handlePaymentCallbackUseCase, handleSignupPaymentCallbackUseCase);
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
    void should_redirectToAppSuccess_when_postCallbackSucceedsAsInvoicePayment() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
        verify(paymentGatewayPort, never()).isConfigured();
    }

    @Test
    void should_redirectToAppFailure_when_postCallbackReturnsFalseAsInvoicePayment() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_invoiceUseCaseThrowsNonNotFoundDomainException() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceInvalidTransitionException(PlatformInvoiceStatus.PAID, PlatformInvoiceStatus.PAID));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_useCaseThrowsUnexpectedRuntimeException() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new IllegalArgumentException("Invalid UUID string"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_fallBackToSignupSuccess_when_invoiceUseCaseThrowsNotFound() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=basarili");
    }

    @Test
    void should_fallBackToSignupFailure_when_invoiceUseCaseThrowsNotFoundAndSignupReturnsFalse() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=hata");
    }

    @Test
    void should_fallBackToSignupFailure_when_bothUseCasesFindNothing() {
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceNotFoundException(UUID.randomUUID()));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new RuntimeException("bilinmeyen conversationId"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=hata");
    }
}
