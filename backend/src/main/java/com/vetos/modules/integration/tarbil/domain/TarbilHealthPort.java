package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.UUID;

/**
 * Diger moduller (platformadmin -- Sistem Sagligi paneli) basarisiz
 * TARBIL senkronlarina SADECE bu port uzerinden erisir.
 * TarbilSyncLogRepository'yi ASLA import etmezler.
 */
public interface TarbilHealthPort {
    List<FailedTarbilSyncView> findRecentFailed(int limit);

    /**
     * Tenant Detayi sayfasindaki "Entegrasyon Durumu" ozeti icin -- tek bir
     * kiracinin suan FAILED durumdaki TARBIL senkron sayisi.
     */
    long countFailedForTenant(UUID tenantId);
}
