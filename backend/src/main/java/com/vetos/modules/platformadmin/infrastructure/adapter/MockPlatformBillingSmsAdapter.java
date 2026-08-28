package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class MockPlatformBillingSmsAdapter implements PlatformBillingSmsPort {

    @Override
    public void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientPhone) {
        log.info(
            "Platform fatura SMS'i (mock, kesildi): to={}, klinik={}, tutar={}, sonOdemeTarihi={}",
            recipientPhone, tenantName, invoice.getAmount(), invoice.getDueDate()
        );
    }

    @Override
    public void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientPhone) {
        log.info(
            "Platform fatura SMS'i (mock, son gun yaklasiyor): to={}, klinik={}, tutar={}, sonOdemeTarihi={}",
            recipientPhone, tenantName, invoice.getAmount(), invoice.getDueDate()
        );
    }

    @Override
    public void sendTenantSuspended(String tenantName, String recipientPhone) {
        log.info("Platform fatura SMS'i (mock, askiya alindi): to={}, klinik={}", recipientPhone, tenantName);
    }
}
