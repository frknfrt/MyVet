package com.vetos.modules.platformadmin.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceAdminPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.efatura.domain.FailedEInvoiceView;
import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilAdminPort;
import com.vetos.modules.integration.tarbil.domain.TarbilHealthPort;
import com.vetos.modules.notification.domain.FailedNotificationView;
import com.vetos.modules.notification.domain.NotificationAdminPort;
import com.vetos.modules.notification.domain.NotificationHealthPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin "Sistem Sagligi" paneli -- tum kiracilarda son zamanlarda
 * basarisiz olan SMS/WhatsApp bildirimlerini, e-Fatura gonderimlerini ve
 * TARBIL senkronlarini tek ekrandan gorebilmek, ve gerekirse manuel olarak
 * tekrar denemek icin. Her alt sistemin kendi LookupPort/AdminPort'u
 * uzerinden okur/yazar (bkz. NotificationHealthPort, NotificationAdminPort,
 * EInvoiceHealthPort, EInvoiceAdminPort, TarbilHealthPort, TarbilAdminPort)
 * -- ilgili modullerin repository'lerini ASLA dogrudan import etmez.
 */
@Service
@RequiredArgsConstructor
public class ListPlatformSystemHealthUseCase {

    private static final int DEFAULT_LIMIT = 100;

    private final NotificationHealthPort notificationHealthPort;
    private final NotificationAdminPort notificationAdminPort;
    private final EInvoiceHealthPort eInvoiceHealthPort;
    private final EInvoiceAdminPort eInvoiceAdminPort;
    private final TarbilHealthPort tarbilHealthPort;
    private final TarbilAdminPort tarbilAdminPort;

    public List<FailedNotificationView> failedNotifications() {
        return notificationHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }

    public List<FailedEInvoiceView> failedEInvoices() {
        return eInvoiceHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }

    public List<FailedTarbilSyncView> failedTarbilSyncs() {
        return tarbilHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }

    public void retryNotification(UUID notificationLogId) {
        notificationAdminPort.retryNow(notificationLogId);
    }

    public void retryEInvoice(UUID submissionId) {
        eInvoiceAdminPort.retryNow(submissionId);
    }

    public void retryTarbilSync(UUID syncLogId) {
        tarbilAdminPort.retryNow(syncLogId);
    }
}
