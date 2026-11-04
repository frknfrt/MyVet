package com.vetos.modules.platformadmin.domain;

/**
 * Platform faturalama e-posta bildirimlerini soyutlayan port -- davet
 * e-postasi ile ayni desen (@docs/architecture.md Bolum 3). admin-email.*
 * (cPanel bildirim@ kutusu) tanimliysa SmtpPlatformBillingEmailAdapter
 * gercekten gonderir, tanimli degilse sadece loglar.
 */
public interface PlatformBillingEmailPort {
    void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientEmail);
    void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientEmail);
    void sendTenantSuspended(String tenantName, String recipientEmail);
}
