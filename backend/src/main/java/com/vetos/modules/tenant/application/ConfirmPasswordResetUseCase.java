package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.PasswordResetToken;
import com.vetos.modules.tenant.domain.PasswordResetTokenRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.PasswordResetTokenInvalidException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Bağlantıdaki token ile yeni şifre belirlenir; token bir kez kullanılır. Hatalı her durumda aynı mesaj döner. */
@Service
@RequiredArgsConstructor
public class ConfirmPasswordResetUseCase {

    private final PasswordResetTokenRepository tokenRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditPort auditPort;

    @Transactional
    public void execute(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) throw new PasswordResetTokenInvalidException();
        Instant now = Instant.now();
        PasswordResetToken token = tokenRepository.findByTokenHash(PasswordResetTokens.hash(rawToken))
            .filter(t -> t.isUsable(now))
            .orElseThrow(PasswordResetTokenInvalidException::new);
        StaffUser user = staffUserRepository.findById(token.getStaffUserId())
            .filter(StaffUser::isActive)
            .orElseThrow(PasswordResetTokenInvalidException::new);

        user.changePassword(passwordEncoder.encode(newPassword));
        staffUserRepository.save(user);
        token.consume(now);
        tokenRepository.save(token);

        // kimliksiz uç: kiracı token'dan, aktör kullanıcının kendisi
        auditPort.record(
            AuditRecord.of(AuditAction.STAFF_PASSWORD_RESET, "STAFF", user.getId(), user.getFullName(), null)
                .forTenant(user.getTenantId()),
            AuditActor.staff(user.getId(), user.getFullName(), user.getRole().name())
        );
    }
}
