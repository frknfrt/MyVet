package com.vetos.modules.platformadmin.domain;

/**
 * Platform faturalama SMS bildirimlerini soyutlayan port. Mevcut
 * modules.notification'daki NotificationSendPort BILINCLI olarak
 * kullanilmadi -- o modul kiraci-ici (klinik -> hayvan sahibi) mesajlasma
 * icin tasarlandi ve TenantContext'e bagimli; platform admin aksiyonlari
 * kiracilar-arasi oldugu icin platformadmin kendi izole altyapisini kurar
 * (architecture.md SS6.1'deki paralel altyapi felsefesiyle tutarli).
 * Gercek bir SMS saglayicisi hesabi bu ortamda yok;
 * MockPlatformBillingSmsAdapter bu portu simule eder.
 */
public interface PlatformBillingSmsPort {
    void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientPhone);
    void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientPhone);
    void sendTenantSuspended(String tenantName, String recipientPhone);
}
