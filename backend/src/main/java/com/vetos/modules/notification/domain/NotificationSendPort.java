package com.vetos.modules.notification.domain;

/**
 * @docs/architecture.md Bolum 3 (Open/Closed) -- yeni bir saglayici
 * (Twilio, Netgsm, WhatsApp Business API...) eklemek icin tek yapilan: bu
 * arayuzu implemente eden yeni bir @Component yazmak. Gercek saglayici
 * hesabi/API anahtari gelene kadar MockNotificationAdapter kullanilir
 * (@docs/architecture.md TARBIL/MockTarbilAdapter ile ayni desen).
 */
public interface NotificationSendPort {
    NotificationSendOutcome send(NotificationSendRequest request);
}
