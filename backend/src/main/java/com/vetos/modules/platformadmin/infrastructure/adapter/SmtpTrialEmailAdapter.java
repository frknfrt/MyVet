package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.TrialEmailPort;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deneme e-postalari (hos geldiniz, 2 gun kaldi, son gun) -- SmtpPlatformBillingEmailAdapter ile
 * ayni bildirim@ kutusu ve ayni davranis: SMTP tanimli degilse simule eder, gonderim hatasinda
 * exception FIRLATMAZ.
 */
@Component
@Slf4j
class SmtpTrialEmailAdapter implements TrialEmailPort {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SmtpMailClient smtpMailClient;
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    SmtpTrialEmailAdapter(
        SmtpMailClient smtpMailClient,
        @Value("${admin-email.smtp-host:}") String smtpHost,
        @Value("${admin-email.smtp-port:587}") int smtpPort,
        @Value("${admin-email.bildirim.username:}") String username,
        @Value("${admin-email.bildirim.password:}") String password
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
    public void sendWelcome(String email, String fullName, String clinicName, LocalDate trialEndsAt, String loginUrl) {
        send("email-templates/trial-welcome.html", "Vetly denemeniz başladı — " + clinicName, Map.of(
            "AD_SOYAD", escapeHtml(fullName),
            "KLINIK_ADI", escapeHtml(clinicName),
            "BITIS_TARIHI", DATE_FORMAT.format(trialEndsAt),
            "GIRIS_ADRESI", escapeHtml(loginUrl)
        ), email);
    }

    @Override
    public void sendTrialEndingSoon(String email, String clinicName, LocalDate trialEndsAt, String upgradeUrl) {
        send("email-templates/trial-ending-soon.html", "Vetly denemenizin bitmesine 2 gün kaldı — " + clinicName, Map.of(
            "KLINIK_ADI", escapeHtml(clinicName),
            "BITIS_TARIHI", DATE_FORMAT.format(trialEndsAt),
            "UYELIK_ADRESI", escapeHtml(upgradeUrl)
        ), email);
    }

    @Override
    public void sendTrialLastDay(String email, String clinicName, String upgradeUrl) {
        send("email-templates/trial-last-day.html", "Vetly denemeniz bugün sona eriyor — " + clinicName, Map.of(
            "KLINIK_ADI", escapeHtml(clinicName),
            "UYELIK_ADRESI", escapeHtml(upgradeUrl)
        ), email);
    }

    private void send(String templatePath, String subject, Map<String, String> fields, String recipientEmail) {
        if (!isConfigured()) {
            log.info("Deneme e-postasi (mock, SMTP tanimli degil): to={}, sablon={}", LogMasking.email(recipientEmail), templatePath);
            return;
        }
        String html = render(templatePath, fields);
        try {
            smtpMailClient.send(smtpHost, smtpPort, username, password, "bildirim@vetly.com.tr", recipientEmail, subject, html);
        } catch (SmtpSendException e) {
            log.warn("Deneme e-postasi gonderilemedi: to={}, sablon={}", LogMasking.email(recipientEmail), templatePath, e);
        }
    }

    // paket-ozel: sablon/kacislama testi dogrudan cagirir
    String render(String templatePath, Map<String, String> fields) {
        String result = templateCache.computeIfAbsent(templatePath, this::readClasspathResource);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String readClasspathResource(String path) {
        try {
            return new String(new ClassPathResource(path).getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Deneme e-postasi sablonu okunamadi: " + path, e);
        }
    }
}
