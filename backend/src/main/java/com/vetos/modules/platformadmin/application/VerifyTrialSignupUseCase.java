package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialEmailPort;
import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestStatus;
import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.platformadmin.domain.exception.SignupEmailAlreadyRegisteredConflictException;
import com.vetos.modules.platformadmin.domain.exception.TrialCodeExpiredException;
import com.vetos.modules.platformadmin.domain.exception.TrialCodeInvalidException;
import com.vetos.modules.platformadmin.domain.exception.TrialPhoneAlreadyUsedConflictException;
import com.vetos.modules.platformadmin.domain.exception.TrialSignupRequestNotFoundException;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.subscription.TrialProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Self-servis deneme kaydinin ikinci adimi: SMS kodu dogruysa klinigi, ilk subeyi ve yonetici
 * hesabini olusturur, 7 gunluk denemeyi baslatir. Idempotent: tamamlanmis bir istek tekrar
 * dogrulanirsa klinik yeniden olusturulmaz.
 */
@Service
@Slf4j
public class VerifyTrialSignupUseCase {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final TrialSignupRequestRepository repository;
    private final TenantAdminPort tenantAdminPort;
    private final PasswordEncoder passwordEncoder;
    private final TrialSmsPort trialSmsPort;
    private final TrialEmailPort trialEmailPort;
    private final TrialProperties properties;
    private final String frontendBaseUrl;
    private final Clock clock;

    @Autowired
    public VerifyTrialSignupUseCase(
        TrialSignupRequestRepository repository, TenantAdminPort tenantAdminPort, PasswordEncoder passwordEncoder,
        TrialSmsPort trialSmsPort, TrialEmailPort trialEmailPort, TrialProperties properties,
        @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this(repository, tenantAdminPort, passwordEncoder, trialSmsPort, trialEmailPort, properties, frontendBaseUrl, Clock.systemUTC());
    }

    VerifyTrialSignupUseCase(
        TrialSignupRequestRepository repository, TenantAdminPort tenantAdminPort, PasswordEncoder passwordEncoder,
        TrialSmsPort trialSmsPort, TrialEmailPort trialEmailPort, TrialProperties properties,
        String frontendBaseUrl, Clock clock
    ) {
        this.repository = repository;
        this.tenantAdminPort = tenantAdminPort;
        this.passwordEncoder = passwordEncoder;
        this.trialSmsPort = trialSmsPort;
        this.trialEmailPort = trialEmailPort;
        this.properties = properties;
        this.frontendBaseUrl = frontendBaseUrl;
        this.clock = clock;
    }

    /**
     * noRollbackFor: yanlis kodda deneme sayaci artirilip kaydedilir ve istisna firlatilir;
     * sayac artisi geri alinmamali, yoksa kod sinirsiz denenebilir.
     *
     * @return panelin giris adresi (e-posta dolu gelir)
     */
    @Transactional(noRollbackFor = TrialCodeInvalidException.class)
    public String execute(UUID requestId, String code) {
        TrialSignupRequest request = repository.findByIdForUpdate(requestId)
            .orElseThrow(TrialSignupRequestNotFoundException::new);
        String loginUrl = loginUrlFor(request.getEmail());
        if (request.isCompleted()) {
            return loginUrl;
        }

        Instant now = clock.instant();
        if (!request.hasAttemptsLeft(properties.otpMaxAttempts())) {
            throw new TrialCodeInvalidException(0);
        }
        if (request.isCodeExpired(now)) {
            throw new TrialCodeExpiredException();
        }
        if (code == null || !passwordEncoder.matches(code.trim(), request.getOtpHash())) {
            request.registerFailedAttempt();
            repository.save(request);
            throw new TrialCodeInvalidException(request.attemptsLeft(properties.otpMaxAttempts()));
        }

        // Kayit ile dogrulama arasinda ayni e-posta/telefonla baska bir hesap acilmis olabilir.
        if (tenantAdminPort.isEmailRegistered(request.getEmail())) {
            throw new SignupEmailAlreadyRegisteredConflictException(request.getEmail());
        }
        if (repository.existsByPhoneAndStatus(request.getPhone(), TrialSignupRequestStatus.COMPLETED)) {
            throw new TrialPhoneAlreadyUsedConflictException();
        }

        UUID tenantId = tenantAdminPort.createTrialTenant(
            request.getClinicName(), request.getCity(), request.getFullName(), request.getEmail(),
            request.getPasswordHash(), request.getPhone(), request.getChamberRegistrationNo()
        );
        String fullName = request.getFullName();
        request.complete(tenantId, now);
        repository.save(request);

        LocalDate trialEndsAt = LocalDate.now(clock.withZone(ISTANBUL)).plusDays(properties.days());
        notifyWelcome(request, fullName, trialEndsAt, loginUrl);
        return loginUrl;
    }

    /** Bildirim hatasi kaydi geri almamali: klinik olustu, kullanici giris yapabilmeli. */
    private void notifyWelcome(TrialSignupRequest request, String fullName, LocalDate trialEndsAt, String loginUrl) {
        try {
            trialSmsPort.sendWelcome(request.getPhone(), request.getClinicName(), trialEndsAt, frontendBaseUrl + "/login");
        } catch (RuntimeException e) {
            log.warn("Deneme hos geldiniz SMS'i gonderilemedi: requestId={}, hata={}", request.getId(), e.getMessage());
        }
        try {
            trialEmailPort.sendWelcome(request.getEmail(), fullName, request.getClinicName(), trialEndsAt, loginUrl);
        } catch (RuntimeException e) {
            log.warn("Deneme hos geldiniz e-postasi gonderilemedi: requestId={}, hata={}", request.getId(), e.getMessage());
        }
    }

    private String loginUrlFor(String email) {
        return frontendBaseUrl + "/login?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&deneme=1";
    }
}
