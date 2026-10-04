package com.vetos.modules.integration.efatura.domain;

import java.util.List;
import java.util.UUID;

/**
 * Diger moduller (platformadmin -- Sistem Sagligi paneli) basarisiz
 * e-Fatura gonderimlerine SADECE bu port uzerinden erisir.
 * EInvoiceSubmissionRepository'yi ASLA import etmezler.
 */
public interface EInvoiceHealthPort {
    List<FailedEInvoiceView> findRecentFailed(int limit);

    /**
     * Tenant Detayi sayfasindaki "Entegrasyon Durumu" ozeti icin -- tek bir
     * kiracinin suan FAILED durumdaki e-Fatura gonderim sayisi.
     */
    long countFailedForTenant(UUID tenantId);
}
