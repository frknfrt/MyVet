package com.vetos.modules.tenant.domain;

/**
 * Davet e-postasi gonderimini soyutlayan port -- TARBIL/e-Fatura/notification
 * ile ayni desen (@docs/architecture.md Bolum 3). Gercek bir e-posta
 * saglayicisi (SendGrid/SMTP) hesabi bu ortamda yok; MockInviteEmailAdapter
 * bu portu simule eder. Gercek saglayici eklendiginde sadece adaptor degisir.
 */
public interface InviteEmailPort {
    void sendInvite(StaffInvite invite, String tenantName, String acceptUrl);
}
