package com.vetos.modules.integration.efatura.domain;

/**
 * @docs/architecture.md Bolum 3 (Open/Closed) -- yeni bir saglayici (GIB
 * dogrudan entegrasyonu, Foriba, Uyumsoft, Logo...) eklemek icin tek
 * yapilan: bu arayuzu implemente eden yeni bir @Component yazmak. Gercek
 * saglayici hesabi/API anahtari gelene kadar MockEInvoiceGatewayAdapter
 * kullanilir (@docs/architecture.md TARBIL/MockTarbilAdapter ile ayni desen).
 */
public interface EInvoiceGatewayPort {
    EInvoiceSubmissionOutcome submit(EInvoiceSubmissionRequest request);
}
