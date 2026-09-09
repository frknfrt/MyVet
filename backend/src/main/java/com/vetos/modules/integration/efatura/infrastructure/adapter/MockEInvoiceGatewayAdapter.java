package com.vetos.modules.integration.efatura.infrastructure.adapter;

import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionOutcome;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gercek bir e-Fatura/e-Arsiv saglayici hesabi/API anahtari yapilandirilmamissa
 * (efatura.provider=mock, varsayilan) devrede olan stub -- basari/hata
 * davranisini simule eder ki durum ekrani (Ayarlar > e-Fatura) gercekci
 * bicimde test edilebilsin (MockTarbilAdapter ile ayni desen). efatura.provider=
 * faturaentegrator oldugunda yerini FaturaEntegratorEInvoiceGatewayAdapter alir.
 */
@Component
@ConditionalOnProperty(prefix = "efatura", name = "provider", havingValue = "mock", matchIfMissing = true)
@Slf4j
class MockEInvoiceGatewayAdapter implements EInvoiceGatewayPort {

    private static final double SIMULATED_FAILURE_RATE = 0.1;

    @Override
    public EInvoiceSubmissionOutcome submit(EInvoiceSubmissionRequest request) {
        log.info(
            "e-Fatura gonderimi (mock): tur={}, alici={}, tutar={}, KDV={}",
            request.documentType(), request.buyerName(), request.totalAmount(), request.taxAmount()
        );

        if (Math.random() < SIMULATED_FAILURE_RATE) {
            return EInvoiceSubmissionOutcome.failure("GIB servisi gecici olarak yanit vermedi (simule edilmis hata)");
        }

        String gibReference = UUID.randomUUID().toString();
        return EInvoiceSubmissionOutcome.success(gibReference, "Kabul edildi (mock)");
    }
}
