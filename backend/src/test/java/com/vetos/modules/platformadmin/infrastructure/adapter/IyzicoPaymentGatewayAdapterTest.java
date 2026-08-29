package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.CheckoutSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoPaymentGatewayAdapterTest {

    private final IyzicoPaymentGatewayAdapter adapter =
        new IyzicoPaymentGatewayAdapter("", "", "https://sandbox-api.iyzipay.com", "http://localhost:8080");

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
}
