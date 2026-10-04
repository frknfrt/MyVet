package com.vetos.modules.platformadmin.domain;

/**
 * Platform duyurusu e-postasini soyutlayan port -- PlatformBillingEmailPort
 * ile AYNI kisit (bkz. o arayuzun javadoc'u): gercek bir e-posta saglayicisi
 * bu ortamda yok, MockPlatformAnnouncementEmailAdapter bu portu simule eder.
 */
public interface PlatformAnnouncementEmailPort {
    void sendAnnouncement(String tenantName, String recipientEmail, String title, String body);
}
