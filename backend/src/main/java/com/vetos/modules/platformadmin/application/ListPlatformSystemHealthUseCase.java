package com.vetos.modules.platformadmin.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.efatura.domain.FailedEInvoiceView;
import com.vetos.modules.notification.domain.FailedNotificationView;
import com.vetos.modules.notification.domain.NotificationHealthPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Platform admin "Sistem Sagligi" paneli -- tum kiracilarda son zamanlarda
 * basarisiz olan SMS/WhatsApp bildirimlerini ve e-Fatura gonderimlerini tek
 * ekrandan gorebilmek icin. Her iki alt sistemin kendi LookupPort'u
 * uzerinden okur (bkz. NotificationHealthPort, EInvoiceHealthPort) --
 * ilgili modullerin repository'lerini ASLA dogrudan import etmez.
 */
@Service
@RequiredArgsConstructor
public class ListPlatformSystemHealthUseCase {

    private static final int DEFAULT_LIMIT = 100;

    private final NotificationHealthPort notificationHealthPort;
    private final EInvoiceHealthPort eInvoiceHealthPort;

    public List<FailedNotificationView> failedNotifications() {
        return notificationHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }

    public List<FailedEInvoiceView> failedEInvoices() {
        return eInvoiceHealthPort.findRecentFailed(DEFAULT_LIMIT);
    }
}
