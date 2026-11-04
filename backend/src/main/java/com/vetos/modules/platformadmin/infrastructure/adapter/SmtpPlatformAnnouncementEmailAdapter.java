package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformAnnouncementEmailPort;
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

/**
 * Platform duyuru e-postalarini (SendAnnouncementUseCase) cPanel'deki info@ kutusunun SMTP'si
 * uzerinden gercekten gonderir -- SmtpAdminEmailAdapter ile AYNI kimlik bilgilerini kullanir
 * (admin-email.info.*, yeni bir kutu/saglayici GEREKMEZ). SmtpInviteEmailAdapter ile AYNI desen:
 * cagiran taraf tum (askiya alinmamis) kiracilari tek tek gezer, bir SMTP hatasi butun duyuru
 * gonderimini durdurmamali -- bu yuzden gonderim basarisiz olsa da ASLA exception FIRLATMAZ,
 * sadece loglar.
 */
@Component
@Slf4j
class SmtpPlatformAnnouncementEmailAdapter implements PlatformAnnouncementEmailPort {

    private static final String TEMPLATE_PATH = "email-templates/platform-announcement.html";

    private final SmtpMailClient smtpMailClient;
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private String cachedTemplate;

    SmtpPlatformAnnouncementEmailAdapter(
        SmtpMailClient smtpMailClient,
        @Value("${admin-email.smtp-host:}") String smtpHost,
        @Value("${admin-email.smtp-port:587}") int smtpPort,
        @Value("${admin-email.info.username:}") String username,
        @Value("${admin-email.info.password:}") String password
    ) {
        this.smtpMailClient = smtpMailClient;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
    }

    private boolean isConfigured() {
        return !smtpHost.isBlank() && !username.isBlank() && !password.isBlank();
    }

    @Override
    public void sendAnnouncement(String tenantName, String recipientEmail, String title, String body) {
        if (!isConfigured()) {
            log.info("Platform duyuru e-postasi (mock, SMTP tanimli degil): to={}, klinik={}, baslik={}", LogMasking.email(recipientEmail), tenantName, title);
            return;
        }

        String html = loadTemplate()
            .replace("{{KLINIK_ADI}}", escapeHtml(tenantName))
            .replace("{{BASLIK}}", escapeHtml(title))
            .replace("{{GOVDE}}", toHtmlParagraph(body));

        try {
            smtpMailClient.send(smtpHost, smtpPort, username, password, "info@vetly.com.tr", recipientEmail, title, html);
        } catch (SmtpSendException e) {
            log.warn("Platform duyuru e-postasi gonderilemedi: to={}, klinik={}, baslik={}", LogMasking.email(recipientEmail), tenantName, title, e);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Admin'in ozgur metnindeki satir sonlari <br> olarak korunur, HTML olarak kacislanir. */
    private static String toHtmlParagraph(String body) {
        return escapeHtml(body).replace("\r\n", "\n").replace("\n", "<br>");
    }

    private synchronized String loadTemplate() {
        if (cachedTemplate == null) {
            try {
                byte[] bytes = new ClassPathResource(TEMPLATE_PATH).getContentAsByteArray();
                cachedTemplate = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Platform duyuru e-postasi sablonu okunamadi", e);
            }
        }
        return cachedTemplate;
    }
}
