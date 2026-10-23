package com.vetos.modules.platformadmin.domain;

/**
 * Admin panelinden elle tetiklenen marka e-postalarinin gonderimini
 * soyutlayan port -- PlatformBillingEmailPort/PlatformAnnouncementEmailPort
 * ile AYNI "gercek saglayici yoksa simule et" deseni, ama onlardan farkli
 * olarak burada gercek bir hedef VAR: cPanel'deki 3 kutunun kendi SMTP
 * sunucusu (yeni bir saglayici hesabi gerekmez). isConfigured() false ise
 * SmtpAdminEmailAdapter gonderimi sadece loglar.
 */
public interface AdminEmailPort {
    boolean isConfigured();
    void send(AdminMailbox mailbox, String recipientEmail, String subject, String html);
}
