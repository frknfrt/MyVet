package com.vetos.modules.tenant.infrastructure.adapter;

import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffRole;
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
import java.util.EnumMap;
import java.util.Map;

/**
 * Davet e-postasini (hem yeni odeme yapan kliniklerin ilk admini, hem de
 * mevcut bir klinige eklenen personel icin -- bkz. InviteEmailPort'un iki
 * cagiran yeri: HandleSignupPaymentCallbackUseCase ve InviteStaffMemberUseCase)
 * cPanel'deki destek@ kutusunun SMTP'si uzerinden gercekten gonderir --
 * admin-email.destek.* ile AYNI kimlik bilgilerini kullanir (SmtpAdminEmailAdapter
 * ile paylasilan config, yeni bir kutu/saglayici GEREKMEZ).
 *
 * KRITIK: her iki cagiran da bu metodu KENDI @Transactional islemi icinde
 * cagirir -- HandleSignupPaymentCallbackUseCase'de bu islem gercek bir
 * iyzico odemesinin hemen sonrasidir (musteri zaten veznesinden cekildi).
 * Bu yuzden bu adaptor SmtpAdminEmailAdapter'dan FARKLI olarak gonderim
 * basarisiz olsa da ASLA exception FIRLATMAZ -- sadece loglar. Aksi halde
 * bir SMTP hatasi, parasi cekilmis bir musterinin hesap/fatura kaydinin
 * transaction rollback ile silinmesine yol acardi.
 */
@Component
@Slf4j
class SmtpInviteEmailAdapter implements InviteEmailPort {

    private static final Map<StaffRole, String> ROLE_LABELS = Map.of(
        StaffRole.VET, "Veteriner",
        StaffRole.TECHNICIAN, "Teknisyen",
        StaffRole.RECEPTIONIST, "Resepsiyonist",
        StaffRole.ADMIN, "Yönetici",
        StaffRole.OWNER_ACCOUNT, "Klinik Sahibi"
    );

    private final SmtpMailClient smtpMailClient;
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private String cachedTemplate;

    SmtpInviteEmailAdapter(
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

    @Override
    public void sendInvite(StaffInvite invite, String tenantName, String acceptUrl) {
        if (!isConfigured()) {
            log.info(
                "Davet e-postasi (mock, SMTP tanimli degil): to={}, klinik={}, rol={}, link={}",
                LogMasking.email(invite.getEmail()), tenantName, invite.getRole(), acceptUrl
            );
            return;
        }

        String html = loadTemplate()
            .replace("{{TENANT_NAME}}", tenantName)
            .replace("{{FULL_NAME}}", invite.getFullName())
            .replace("{{ROLE_LABEL}}", ROLE_LABELS.getOrDefault(invite.getRole(), invite.getRole().name()))
            .replace("{{ACCEPT_URL}}", acceptUrl);

        try {
            smtpMailClient.send(
                smtpHost, smtpPort, username, password, "destek@vetly.com.tr", invite.getEmail(),
                tenantName + " ekibine davet edildiniz", html
            );
        } catch (SmtpSendException e) {
            // Bilerek yutuluyor -- bkz. sinif javadoc'u. Admin, Ayarlar > Kullanicilar'daki
            // "Bekleyen Davetler" listesinden linki manuel kopyalayip paylasabilir.
            log.warn("Davet e-postasi gonderilemedi: to={}, klinik={}", LogMasking.email(invite.getEmail()), tenantName, e);
        }
    }

    private synchronized String loadTemplate() {
        if (cachedTemplate == null) {
            try {
                byte[] bytes = new ClassPathResource("email-templates/invite.html").getContentAsByteArray();
                cachedTemplate = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Davet e-postasi sablonu okunamadi", e);
            }
        }
        return cachedTemplate;
    }
}
