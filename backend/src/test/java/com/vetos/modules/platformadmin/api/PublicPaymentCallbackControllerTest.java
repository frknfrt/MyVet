package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.application.HandlePaymentCallbackUseCase;
import com.vetos.modules.platformadmin.application.HandleSignupPaymentCallbackUseCase;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    @Mock private TenantSignupRequestRepository tenantSignupRequestRepository;

    private PublicPaymentCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPaymentCallbackController(
            handlePaymentCallbackUseCase, handleSignupPaymentCallbackUseCase, paymentGatewayPort,
            tenantSignupRequestRepository, FRONTEND, VETLY_SITE
        );
    }

    private static String location(ResponseEntity<Void> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return String.valueOf(response.getHeaders().getLocation());
    }

    private void stubCheckoutResult(String token, UUID conversationId) {
        when(paymentGatewayPort.retrieveCheckoutResult(token))
            .thenReturn(new CheckoutResult(true, conversationId.toString(), "pay_123"));
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
        UUID invoiceId = UUID.randomUUID();
        when(paymentGatewayPort.isConfigured()).thenReturn(false);
        stubCheckoutResult("SIMULATED-TOK", invoiceId);
        when(tenantSignupRequestRepository.findById(invoiceId)).thenReturn(Optional.empty());
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        ResponseEntity<Void> response = controller.handleGet("SIMULATED-TOK");

        assertThat(location(response)).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verify(handlePaymentCallbackUseCase).execute(anyString(), any(LocalDate.class));
    }

    @Test
    void should_redirectToAppSuccess_when_postCallbackSucceedsAsInvoicePayment() {
        UUID invoiceId = UUID.randomUUID();
        stubCheckoutResult("token-1", invoiceId);
        when(tenantSignupRequestRepository.findById(invoiceId)).thenReturn(Optional.empty());
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=basarili");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
        verify(paymentGatewayPort, never()).isConfigured();
    }

    @Test
    void should_redirectToAppFailure_when_postCallbackReturnsFalseAsInvoicePayment() {
        UUID invoiceId = UUID.randomUUID();
        stubCheckoutResult("token-1", invoiceId);
        when(tenantSignupRequestRepository.findById(invoiceId)).thenReturn(Optional.empty());
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_invoiceUseCaseThrowsNonNotFoundDomainException() {
        UUID invoiceId = UUID.randomUUID();
        stubCheckoutResult("token-1", invoiceId);
        when(tenantSignupRequestRepository.findById(invoiceId)).thenReturn(Optional.empty());
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new PlatformInvoiceInvalidTransitionException(PlatformInvoiceStatus.PAID, PlatformInvoiceStatus.PAID));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_useCaseThrowsUnexpectedRuntimeException() {
        UUID invoiceId = UUID.randomUUID();
        stubCheckoutResult("token-1", invoiceId);
        when(tenantSignupRequestRepository.findById(invoiceId)).thenReturn(Optional.empty());
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new IllegalStateException("beklenmedik hata"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
    }

    @Test
    void should_redirectToAppFailure_when_checkoutResultCannotBeRetrieved() {
        when(paymentGatewayPort.retrieveCheckoutResult("token-1")).thenThrow(new RuntimeException("gateway hatasi"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handlePaymentCallbackUseCase, handleSignupPaymentCallbackUseCase, tenantSignupRequestRepository);
    }

    @Test
    void should_routeToInvoiceFlow_when_conversationIdIsMalformed() {
        when(paymentGatewayPort.retrieveCheckoutResult("token-1"))
            .thenReturn(new CheckoutResult(false, "not-a-uuid", null));
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
        verifyNoInteractions(tenantSignupRequestRepository);
    }

    @Test
    void should_routeToInvoiceFlow_when_conversationIdIsNull() {
        when(paymentGatewayPort.retrieveCheckoutResult("token-1"))
            .thenReturn(new CheckoutResult(false, null, null));
        when(handlePaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(FRONTEND + "/ayarlar/abonelik?odeme=hata");
        verifyNoInteractions(handleSignupPaymentCallbackUseCase);
        verifyNoInteractions(tenantSignupRequestRepository);
    }

    @Test
    void should_routeToSignupFlow_when_conversationIdMatchesTenantSignupRequest() {
        UUID requestId = UUID.randomUUID();
        stubCheckoutResult("token-1", requestId);
        when(tenantSignupRequestRepository.findById(requestId)).thenReturn(Optional.of(mock(TenantSignupRequest.class)));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(true);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=basarili");
        verifyNoInteractions(handlePaymentCallbackUseCase);
    }

    @Test
    void should_redirectToVetlySite_notApp_when_declinedSignupPaymentIsRoutedByIdentity() {
        // This is the exact bug being fixed: a DECLINED signup payment must redirect to
        // vetly-site, never to the authenticated app's /ayarlar/abonelik page.
        UUID requestId = UUID.randomUUID();
        stubCheckoutResult("token-1", requestId);
        when(tenantSignupRequestRepository.findById(requestId)).thenReturn(Optional.of(mock(TenantSignupRequest.class)));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class))).thenReturn(false);

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=hata");
        verifyNoInteractions(handlePaymentCallbackUseCase);
    }

    @Test
    void should_redirectToSignupFailure_when_signupUseCaseThrows() {
        UUID requestId = UUID.randomUUID();
        stubCheckoutResult("token-1", requestId);
        when(tenantSignupRequestRepository.findById(requestId)).thenReturn(Optional.of(mock(TenantSignupRequest.class)));
        when(handleSignupPaymentCallbackUseCase.execute(anyString(), any(LocalDate.class)))
            .thenThrow(new RuntimeException("beklenmedik hata"));

        assertThat(location(controller.handlePost("token-1"))).isEqualTo(VETLY_SITE + "/?kayit=hata");
    }
}
