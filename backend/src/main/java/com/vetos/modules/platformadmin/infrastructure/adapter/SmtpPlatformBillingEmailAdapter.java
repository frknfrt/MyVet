package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.platform.email.SmtpMailClient;
import com.vetos.platform.email.SmtpSendException;
import com.vetos.platform.observability.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform fatura bildirim e-postalarini (fatura kesildi / son gun yaklasiyor / hesap askiya
 * alindi) cPanel'deki bildirim@ kutusunun SMTP'si uzerinden gercekten gonderir -- SmtpAdminEmailAdapter
 * ile AYNI kimlik bilgilerini kullanir (admin-email.bildirim.*, yeni bir kutu/saglayici GEREKMEZ).
 * SmtpInviteEmailAdapter ile AYNI "transaction icinde gonderim" deseni: cagiran taraf
 * (GenerateDueInvoicesUseCase/RemindDueSoonInvoicesUseCase/FlagOverdueAndSuspendUseCase) her
 * tenant'i tek tek isler, bir SMTP hatasi gunun butun faturalama isini durdurmamali -- bu yuzden
 * gonderim basarisiz olsa da ASLA exception FIRLATMAZ, sadece loglar (tenant SMS ile de bilgilendirilir,
 * bkz. IletiMerkeziPlatformBillingSmsAdapter).
 */
@Component
@Slf4j
class SmtpPlatformBillingEmailAdapter implements PlatformBillingEmailPort {

    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SmtpMailClient smtpMailClient;
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final String paymentInstructions;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    SmtpPlatformBillingEmailAdapter(
        SmtpMailClient smtpMailClient,
        @Value("${admin-email.smtp-host:}") String smtpHost,
        @Value("${admin-email.smtp-port:587}") int smtpPort,
        @Value("${admin-email.bildirim.username:}") String username,
        @Value("${admin-email.bildirim.password:}") String password,
        @Value("${platform-billing.payment-instructions}") String paymentInstructions
    ) {
        this.smtpMailClient = smtpMailClient;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.paymentInstructions = paymentInstructions;
    }

    private boolean isConfigured() {
        return !smtpHost.isBlank() && !username.isBlank() && !password.isBlank();
    }

    @Override
    public void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientEmail) {
        Map<String, String> fields = Map.of(
            "KLINIK_ADI", tenantName,
            "TUTAR", invoice.getAmount().toPlainString(),
            "SON_ODEME_TARIHI", DUE_DATE_FORMAT.format(invoice.getDueDate()),
            "ODEME_TALIMATI", paymentInstructions
        );
        send("email-templates/platform-invoice-issued.html", "Vetly Fatura Bildirimi — " + tenantName, fields, recipientEmail, tenantName);
    }

    @Override
    public void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientEmail) {
        Map<String, String> fields = Map.of(
            "KLINIK_ADI", tenantName,
            "TUTAR", invoice.getAmount().toPlainString(),
            "SON_ODEME_TARIHI", DUE_DATE_FORMAT.format(invoice.getDueDate()),
            "ODEME_TALIMATI", paymentInstructions
        );
        send("email-templates/platform-invoice-due-soon.html", "Vetly Fatura Son Gün Hatırlatması — " + tenantName, fields, recipientEmail, tenantName);
    }

    @Override
    public void sendTenantSuspended(String tenantName, String recipientEmail) {
        Map<String, String> fields = Map.of(
            "KLINIK_ADI", tenantName,
            "ODEME_TALIMATI", paymentInstructions
        );
        send("email-templates/platform-tenant-suspended.html", "Vetly Hesabınız Askıya Alındı — " + tenantName, fields, recipientEmail, tenantName);
    }

    private void send(String templatePath, String subject, Map<String, String> fields, String recipientEmail, String tenantName) {
        if (!isConfigured()) {
            log.info(
                "Platform fatura e-postasi (mock, SMTP tanimli degil): to={}, klinik={}, sablon={}",
                LogMasking.email(recipientEmail), tenantName, templatePath
            );
            return;
        }

        String html = render(templatePath, fields);
        try {
            smtpMailClient.send(smtpHost, smtpPort, username, password, "bildirim@vetly.com.tr", recipientEmail, subject, html);
        } catch (SmtpSendException e) {
            // Bilerek yutuluyor -- bkz. sinif javadoc'u. Tenant ayni bildirim SMS ile de aliyor.
            log.warn("Platform fatura e-postasi gonderilemedi: to={}, klinik={}, sablon={}", LogMasking.email(recipientEmail), tenantName, templatePath, e);
        }
    }

    private String render(String templatePath, Map<String, String> fields) {
        String template = templateCache.computeIfAbsent(templatePath, this::readClasspathResource);
        String result = template;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String readClasspathResource(String path) {
        try {
            byte[] bytes = new ClassPathResource(path).getContentAsByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Platform fatura e-postasi sablonu okunamadi: " + path, e);
        }
    }
}
