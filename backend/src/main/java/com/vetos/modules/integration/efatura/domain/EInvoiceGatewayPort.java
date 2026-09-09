package com.vetos.modules.integration.efatura.domain;

public interface EInvoiceGatewayPort {
    EInvoiceSubmissionOutcome submit(EInvoiceSubmissionRequest request);

    /** Gercek bir saglayici hesabi/API anahtari yapilandirilmis mi (Ayarlar > e-Fatura ekrani icin). */
    default boolean isConfigured() {
        return false;
    }

    /**
     * Asenkron saglayicilarda (faturaentegrator) callback bildirimi geldiginde
     * guncel durumu sorgulamak icin -- bkz. ApplyEInvoiceCallbackUseCase.
     * Senkron saglayicilar (mock) bu duruma hic girmez, cagirmalari beklenmez.
     */
    default EInvoiceSubmissionOutcome fetchStatus(String providerReference) {
        throw new UnsupportedOperationException("Bu saglayici asenkron durum sorgulamayi desteklemiyor");
    }
}
