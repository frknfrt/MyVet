package com.vetos.modules.integration.tarbil.domain;

import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli -- kalici olarak basarisiz bir
 * TARBIL senkronunu kiraci sinirlamasi olmadan manuel tekrar dener.
 * Diger moduller ASLA TarbilSyncLogRepository'yi dogrudan import etmez,
 * sadece bu port uzerinden yazma erisimi yaparlar (bkz. docs/architecture.md
 * -- platformadmin, AdminPort'a izinli tek modul).
 */
public interface TarbilAdminPort {
    void retryNow(UUID syncLogId);
}
