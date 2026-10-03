package com.vetos.modules.notification.domain;

import java.util.List;

/**
 * Diger moduller (platformadmin -- Sistem Sagligi paneli) basarisiz
 * bildirim kayitlarina SADECE bu port uzerinden erisir.
 * NotificationLogRepository'yi ASLA import etmezler.
 */
public interface NotificationHealthPort {
    List<FailedNotificationView> findRecentFailed(int limit);
}
