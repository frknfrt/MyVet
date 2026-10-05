package com.vetos.modules.integration.efatura.domain;

import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli -- kalici olarak basarisiz bir
 * e-Fatura gonderimini kiraci sinirlamasi olmadan manuel tekrar dener.
 * Diger moduller ASLA EInvoiceSubmissionRepository'yi dogrudan import
 * etmez, sadece bu port uzerinden yazma erisimi yaparlar (bkz.
 * docs/architecture.md -- platformadmin, AdminPort'a izinli tek modul).
 */
public interface EInvoiceAdminPort {
    void retryNow(UUID submissionId);
}
