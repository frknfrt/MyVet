package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class MockPlatformBillingEmailAdapter implements PlatformBillingEmailPort {

    private final String paymentInstructions;

    MockPlatformBillingEmailAdapter(@Value("${platform-billing.payment-instructions}") String paymentInstructions) {
        this.paymentInstructions = paymentInstructions;
    }

    @Override
    public void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientEmail) {
        log.info(
            "Platform fatura e-postasi (mock, kesildi): to={}, klinik={}, tutar={}, sonOdemeTarihi={}, talimat={}",
            recipientEmail, tenantName, invoice.getAmount(), invoice.getDueDate(), paymentInstructions
        );
    }

    @Override
    public void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientEmail) {
        log.info(
            "Platform fatura e-postasi (mock, son gun yaklasiyor): to={}, klinik={}, tutar={}, sonOdemeTarihi={}",
            recipientEmail, tenantName, invoice.getAmount(), invoice.getDueDate()
        );
    }

    @Override
    public void sendTenantSuspended(String tenantName, String recipientEmail) {
        log.info("Platform fatura e-postasi (mock, askiya alindi): to={}, klinik={}", recipientEmail, tenantName);
    }
}
