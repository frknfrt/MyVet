package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.AdminEmailPort;
import com.vetos.modules.platformadmin.domain.AdminMailbox;
import com.vetos.modules.platformadmin.domain.exception.AdminEmailSendServiceUnavailableException;
import com.vetos.platform.email.SmtpMailClient;
import com.vetos.platform.email.SmtpSendException;
import com.vetos.platform.observability.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Admin panelinden elle gonderilen e-postalari cPanel'deki 3 gercek kutunun
 * kendi SMTP sunucusuyla gonderir (bkz. SmtpMailClient) -- yeni bir
 * saglayici (SendGrid/SES/vb.) hesabi GEREKMEZ. IyzicoPaymentGatewayAdapter
 * ile AYNI "bos ise simule et" deseni: host/port + UCU DE kutunun kullanici
 * adi/sifresi birlikte tanimlanmadikca gercek SMTP baglantisi acilmaz,
 * gonderim sadece loglanir (bkz. isConfigured()).
 *
 * Burada gonderim basarisizligi admin'e HATA olarak gosterilir (bkz.
 * AdminEmailSendServiceUnavailableException) -- SmtpInviteEmailAdapter'dan
 * FARKLI olarak, bu akis bir odeme/kritik islem transaction'i icinde
 * calismaz, admin panelden tek seferlik bir aksiyon -- "tekrar dene"
 * demek zarasiz.
 */
@Component
@Slf4j
class SmtpAdminEmailAdapter implements AdminEmailPort {

    private final SmtpMailClient smtpMailClient;
    private final String smtpHost;
    private final int smtpPort;
    private final Map<AdminMailbox, Credentials> credentials = new EnumMap<>(AdminMailbox.class);

    SmtpAdminEmailAdapter(
        SmtpMailClient smtpMailClient,
        @Value("${admin-email.smtp-host:}") String smtpHost,
        @Value("${admin-email.smtp-port:587}") int smtpPort,
        @Value("${admin-email.info.username:}") String infoUsername,
        @Value("${admin-email.info.password:}") String infoPassword,
        @Value("${admin-email.destek.username:}") String destekUsername,
        @Value("${admin-email.destek.password:}") String destekPassword,
        @Value("${admin-email.bildirim.username:}") String bildirimUsername,
        @Value("${admin-email.bildirim.password:}") String bildirimPassword
    ) {
        this.smtpMailClient = smtpMailClient;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        credentials.put(AdminMailbox.INFO, new Credentials(infoUsername, infoPassword));
        credentials.put(AdminMailbox.DESTEK, new Credentials(destekUsername, destekPassword));
        credentials.put(AdminMailbox.BILDIRIM, new Credentials(bildirimUsername, bildirimPassword));
    }

    @Override
    public boolean isConfigured() {
        if (smtpHost.isBlank()) {
            return false;
        }
        return credentials.values().stream().allMatch(Credentials::isComplete);
    }

    @Override
    public void send(AdminMailbox mailbox, String recipientEmail, String subject, String html) {
        if (!isConfigured()) {
            log.info(
                "Admin e-postasi (mock, SMTP tanimli degil): kutu={}, to={}, konu={}",
                mailbox, LogMasking.email(recipientEmail), subject
            );
            return;
        }

        Credentials box = credentials.get(mailbox);
        try {
            smtpMailClient.send(smtpHost, smtpPort, box.username(), box.password(), mailbox.address(), recipientEmail, subject, html);
        } catch (SmtpSendException e) {
            log.warn("Admin e-postasi gonderilemedi: kutu={}, to={}", mailbox, LogMasking.email(recipientEmail), e);
            throw new AdminEmailSendServiceUnavailableException(e);
        }
    }

    private record Credentials(String username, String password) {
        boolean isComplete() {
            return !username.isBlank() && !password.isBlank();
        }
    }
}
