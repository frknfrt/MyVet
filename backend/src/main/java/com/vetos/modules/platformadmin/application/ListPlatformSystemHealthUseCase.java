package com.vetos.modules.platformadmin.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceAdminPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.efatura.domain.FailedEInvoiceView;
import com.vetos.modules.notification.domain.FailedNotificationView;
import com.vetos.modules.notification.domain.NotificationAdminPort;
import com.vetos.modules.notification.domain.NotificationHealthPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin "Sistem Sagligi" paneli -- tum kiracilarda son zamanlarda
 * basarisiz olan SMS/WhatsApp bildirimlerini ve e-Fatura gonderimlerini tek
 * ekrandan gorebilmek, ve gerekirse manuel olarak tekrar denemek icin. Her
 * iki alt sistemin kendi LookupPort/AdminPort'u uzerinden okur/yazar (bkz.
 * NotificationHealthPort, NotificationAdminPort, EInvoiceHealthPort,
 * EInvoiceAdminPort) -- ilgili modullerin repository'lerini ASLA dogrudan
 * import etmez.
 */
@Service
@RequiredArgsConstructor
public class ListPlatformSystemHealthUseCase {

    private static final int DEFAULT_LIMIT = 100;

    private final NotificationHealthPort notificationHealthPort;
    private final NotificationAdminPort notificationAdminPort;
    private final EInvoiceHealthPort eInvoiceHealthPort;
    private final EInvoiceAdminPort eInvoiceAdminPort;

    public List<FailedNotificationView> failedNotifications() {
        return notificationHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }

    public List<FailedEInvoiceView> failedEInvoices() {
        return eInvoiceHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }

    public void retryNotification(UUID notificationLogId) {
        notificationAdminPort.retryNow(notificationLogId);
    }

    public void retryEInvoice(UUID submissionId) {
        eInvoiceAdminPort.retryNow(submissionId);
    }
}
