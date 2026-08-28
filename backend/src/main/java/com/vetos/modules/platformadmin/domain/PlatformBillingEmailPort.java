package com.vetos.modules.platformadmin.domain;

/**
 * Platform faturalama e-posta bildirimlerini soyutlayan port -- TARBIL/davet
 * e-postasi ile ayni desen (@docs/architecture.md Bolum 3). Gercek bir
 * e-posta saglayicisi (SendGrid/SMTP) hesabi bu ortamda yok;
 * MockPlatformBillingEmailAdapter bu portu simule eder.
 */
public interface PlatformBillingEmailPort {
    void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientEmail);
    void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientEmail);
    void sendTenantSuspended(String tenantName, String recipientEmail);
}
