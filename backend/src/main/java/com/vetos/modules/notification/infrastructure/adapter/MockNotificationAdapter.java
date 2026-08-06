package com.vetos.modules.notification.infrastructure.adapter;

import com.vetos.modules.notification.domain.NotificationSendOutcome;
import com.vetos.modules.notification.domain.NotificationSendPort;
import com.vetos.modules.notification.domain.NotificationSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gercek bir SMS/WhatsApp saglayici (Twilio, Netgsm, WhatsApp Business
 * API...) hesabi/API anahtari bu ortamda mevcut degil. Bu adapter, gercek
 * HTTP client'i (@docs/architecture.md Bolum 3 desenindeki gibi)
 * degistirene kadar cagiran kodu (QueueNotificationUseCase, event
 * listener'lar, zamanlanmis hatirlatma isi) hic etkilemeden yerini alan bir
 * stub'tir -- basari/hata davranisini simule eder ki bildirim ekrani
 * (Ayarlar > Bildirimler) gercekci bicimde test edilebilsin
 * (MockTarbilAdapter ile ayni desen).
 */
@Component
@Slf4j
class MockNotificationAdapter implements NotificationSendPort {

    private static final double SIMULATED_FAILURE_RATE = 0.1;

    @Override
    public NotificationSendOutcome send(NotificationSendRequest request) {
        log.info(
            "Bildirim gonderimi (mock): kanal={}, alici={}, mesaj=\"{}\"",
            request.channel(), request.recipientContact(), request.message()
        );

        if (Math.random() < SIMULATED_FAILURE_RATE) {
            return NotificationSendOutcome.failure("Saglayici gecici olarak yanit vermedi (simule edilmis hata)");
        }
        return NotificationSendOutcome.success("Iletildi (mock)");
    }
}
