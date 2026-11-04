package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.PasswordResetEmailPort;
import com.vetos.modules.tenant.domain.PasswordResetToken;
import com.vetos.modules.tenant.domain.PasswordResetTokenRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * "Şifremi unuttum": e-posta kayıtlı ve aktifse 1 saatlik tek kullanımlık bağlantı gönderir.
 * Kayıtlı olmasa da dışarıya aynı sonuç döner (hangi e-postaların kayıtlı olduğu öğrenilemesin);
 * e-posta arka planda gönderildiği için yanıt süresi de bunu ele vermez.
 */
@Service
public class RequestPasswordResetUseCase {

    private final StaffUserRepository staffUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetEmailPort emailPort;
    private final String frontendBaseUrl;

    public RequestPasswordResetUseCase(
        StaffUserRepository staffUserRepository,
        PasswordResetTokenRepository tokenRepository,
        PasswordResetEmailPort emailPort,
        @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.staffUserRepository = staffUserRepository;
        this.tokenRepository = tokenRepository;
        this.emailPort = emailPort;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void execute(String email) {
        if (email == null || email.isBlank()) return;
        Optional<StaffUser> found = findByEmail(email.trim());
        if (found.isEmpty() || !found.get().isActive()) return;
        StaffUser user = found.get();

        Instant now = Instant.now();
        tokenRepository.findUnusedByStaffUserId(user.getId()).forEach(t -> {
            t.consume(now);
            tokenRepository.save(t);
        });

        String rawToken = PasswordResetTokens.newRawToken();
        tokenRepository.save(PasswordResetToken.issue(user.getTenantId(), user.getId(), PasswordResetTokens.hash(rawToken), now));
        emailPort.sendResetLink(user.getEmail(), user.getFullName(), frontendBaseUrl + "/sifre-sifirla/" + rawToken);
    }

    private Optional<StaffUser> findByEmail(String email) {
        Optional<StaffUser> exact = staffUserRepository.findByEmail(email);
        return exact.isPresent() ? exact : staffUserRepository.findByEmail(email.toLowerCase(Locale.ROOT));
    }
}
