package com.vetos.modules.notification.domain;

import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli -- kalici olarak basarisiz
 * (otomatik yeniden deneme haklari tukenmis) bir bildirimi manuel olarak
 * tekrar dener. Diger moduller ASLA NotificationLogRepository'yi dogrudan
 * import etmez, sadece bu port uzerinden yazma erisimi yaparlar (bkz.
 * docs/architecture.md -- platformadmin, AdminPort'a izinli tek modul).
 */
public interface NotificationAdminPort {
    void retryNow(UUID notificationLogId);
}
