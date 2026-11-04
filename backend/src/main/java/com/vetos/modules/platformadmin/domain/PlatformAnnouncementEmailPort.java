package com.vetos.modules.platformadmin.domain;

/**
 * Platform duyurusu e-postasini soyutlayan port -- PlatformBillingEmailPort
 * ile AYNI kisit (bkz. o arayuzun javadoc'u): admin-email.* (cPanel info@
 * kutusu) tanimliysa SmtpPlatformAnnouncementEmailAdapter gercekten gonderir,
 * tanimli degilse sadece loglar.
 */
public interface PlatformAnnouncementEmailPort {
    void sendAnnouncement(String tenantName, String recipientEmail, String title, String body);
}
