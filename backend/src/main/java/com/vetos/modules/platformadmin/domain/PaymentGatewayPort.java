package com.vetos.modules.platformadmin.domain;

import java.math.BigDecimal;

/**
 * Odeme gateway'ini soyutlayan port -- TARBIL/e-posta/SMS portlariyla ayni
 * desen (@docs/architecture.md Bolum 3). Gercek bir iyzico hesabi bu ortamda
 * henuz yok; IyzicoPaymentGatewayAdapter kimlik bilgisi tanimli degilken
 * bu portu simule eder.
 */
public interface PaymentGatewayPort {
    boolean isConfigured();
    CheckoutSession initializeCheckout(String conversationId, BigDecimal amount, String buyerName, String buyerEmail);
    CheckoutResult retrieveCheckoutResult(String token);
}
