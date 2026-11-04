package com.vetos.modules.tenant.infrastructure.adapter;

import com.vetos.modules.tenant.domain.PasswordResetEmailPort;
import com.vetos.platform.email.SmtpMailClient;
import com.vetos.platform.email.SmtpSendException;
import com.vetos.platform.observability.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Şifre sıfırlama e-postası, davet e-postasıyla aynı destek@ SMTP kutusundan (SmtpInviteEmailAdapter).
 * Arka planda (@Async) gönderilir: "Şifremi unuttum" yanıtı e-posta kayıtlı olsa da olmasa da aynı sürede döner.
 * Hata yutulur, yalnız loglanır. SMTP tanımlı değilse (yerel geliştirme) bağlantı log'a yazılır.
 */
@Component
@Slf4j
class SmtpPasswordResetEmailAdapter implements PasswordResetEmailPort {

    private final SmtpMailClient smtpMailClient;
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private String cachedTemplate;

    SmtpPasswordResetEmailAdapter(
        SmtpMailClient smtpMailClient,
        @Value("${admin-email.smtp-host:}") String smtpHost,
        @Value("${admin-email.smtp-port:587}") int smtpPort,
        @Value("${admin-email.destek.username:}") String username,
        @Value("${admin-email.destek.password:}") String password
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

    @Async
    @Override
    public void sendResetLink(String email, String fullName, String resetUrl) {
        if (!isConfigured()) {
            log.info("Şifre sıfırlama e-postası (mock, SMTP tanımlı değil): to={}, link={}", LogMasking.email(email), resetUrl);
            return;
        }
        String html = loadTemplate()
            .replace("{{FULL_NAME}}", HtmlUtils.htmlEscape(fullName))
            .replace("{{RESET_URL}}", resetUrl);
        try {
            smtpMailClient.send(smtpHost, smtpPort, username, password, "destek@vetly.com.tr", email,
                "Vetly şifre sıfırlama", html);
        } catch (SmtpSendException e) {
            log.warn("Şifre sıfırlama e-postası gönderilemedi: to={}", LogMasking.email(email), e);
        }
    }

    private synchronized String loadTemplate() {
        if (cachedTemplate == null) {
            try {
                byte[] bytes = new ClassPathResource("email-templates/password-reset.html").getContentAsByteArray();
                cachedTemplate = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Şifre sıfırlama e-posta şablonu okunamadı", e);
            }
        }
        return cachedTemplate;
    }
}
