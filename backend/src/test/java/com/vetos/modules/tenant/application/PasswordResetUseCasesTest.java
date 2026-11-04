package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.PasswordResetEmailPort;
import com.vetos.modules.tenant.domain.PasswordResetToken;
import com.vetos.modules.tenant.domain.PasswordResetTokenRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.PasswordResetTokenInvalidException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetUseCasesTest {

    @Mock private StaffUserRepository staffUsers;
    @Mock private PasswordResetTokenRepository tokens;
    @Mock private PasswordResetEmailPort emailPort;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditPort auditPort;

    private StaffUser user;

    @BeforeEach
    void setUp() {
        user = StaffUser.register(UUID.randomUUID(), UUID.randomUUID(), "Furkan Fırat", "hekim@klinik.com", "eski-hash", StaffRole.VET);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(tokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RequestPasswordResetUseCase requestUseCase() {
        return new RequestPasswordResetUseCase(staffUsers, tokens, emailPort, "https://uygulama.vetly.com.tr");
    }

    private ConfirmPasswordResetUseCase confirmUseCase() {
        return new ConfirmPasswordResetUseCase(tokens, staffUsers, passwordEncoder, auditPort);
    }

    // --- bağlantı isteme ---

    @Test
    void request_sendsOneHourLinkAndStoresOnlyTheHash() {
        when(staffUsers.findByEmail("hekim@klinik.com")).thenReturn(Optional.of(user));
        when(tokens.findUnusedByStaffUserId(user.getId())).thenReturn(List.of());

        requestUseCase().execute("  Hekim@Klinik.com ");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailPort).sendResetLink(eq("hekim@klinik.com"), eq("Furkan Fırat"), url.capture());
        assertThat(url.getValue()).startsWith("https://uygulama.vetly.com.tr/sifre-sifirla/");
        String rawToken = url.getValue().substring(url.getValue().lastIndexOf('/') + 1);
        assertThat(rawToken).hasSizeGreaterThanOrEqualTo(40);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetTokens.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(3500)).isBefore(Instant.now().plusSeconds(3700));
    }

    @Test
    void request_doesNothingVisibleForUnknownEmail() {
        when(staffUsers.findByEmail(anyString())).thenReturn(Optional.empty());

        requestUseCase().execute("yok@klinik.com");

        verifyNoInteractions(emailPort);
        verify(tokens, never()).save(any());
    }

    @Test
    void request_doesNotSendForDeactivatedUser() {
        user.deactivate();
        when(staffUsers.findByEmail("hekim@klinik.com")).thenReturn(Optional.of(user));

        requestUseCase().execute("hekim@klinik.com");

        verifyNoInteractions(emailPort);
    }

    @Test
    void request_invalidatesEarlierOpenLinks() {
        PasswordResetToken older = PasswordResetToken.issue(user.getTenantId(), user.getId(), "eski", Instant.now());
        when(staffUsers.findByEmail("hekim@klinik.com")).thenReturn(Optional.of(user));
        when(tokens.findUnusedByStaffUserId(user.getId())).thenReturn(List.of(older));

        requestUseCase().execute("hekim@klinik.com");

        assertThat(older.isUsable(Instant.now())).isFalse();
    }

    // --- yeni şifre belirleme ---

    @Test
    void confirm_setsNewPasswordConsumesTokenAndAudits() {
        PasswordResetToken token = PasswordResetToken.issue(user.getTenantId(), user.getId(), PasswordResetTokens.hash("abc"), Instant.now());
        when(tokens.findByTokenHash(PasswordResetTokens.hash("abc"))).thenReturn(Optional.of(token));
        when(staffUsers.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("yeniSifre123")).thenReturn("yeni-hash");

        confirmUseCase().execute("abc", "yeniSifre123");

        assertThat(user.getPasswordHash()).isEqualTo("yeni-hash");
        assertThat(token.isUsable(Instant.now())).isFalse();
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(audit.capture(), any());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.STAFF_PASSWORD_RESET);
    }

    @Test
    void confirm_rejectsUnknownToken() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confirmUseCase().execute("uydurma", "yeniSifre123"))
            .isInstanceOf(PasswordResetTokenInvalidException.class);
        verifyNoInteractions(passwordEncoder, auditPort);
    }

    @Test
    void confirm_rejectsExpiredToken() {
        PasswordResetToken token = PasswordResetToken.issue(user.getTenantId(), user.getId(), PasswordResetTokens.hash("abc"),
            Instant.now().minusSeconds(2 * 3600));
        when(tokens.findByTokenHash(PasswordResetTokens.hash("abc"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> confirmUseCase().execute("abc", "yeniSifre123"))
            .isInstanceOf(PasswordResetTokenInvalidException.class);
        assertThat(user.getPasswordHash()).isEqualTo("eski-hash");
    }

    @Test
    void confirm_rejectsAlreadyUsedToken() {
        PasswordResetToken token = PasswordResetToken.issue(user.getTenantId(), user.getId(), PasswordResetTokens.hash("abc"), Instant.now());
        token.consume(Instant.now());
        when(tokens.findByTokenHash(PasswordResetTokens.hash("abc"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> confirmUseCase().execute("abc", "yeniSifre123"))
            .isInstanceOf(PasswordResetTokenInvalidException.class);
    }

    @Test
    void confirm_rejectsDeactivatedUser() {
        PasswordResetToken token = PasswordResetToken.issue(user.getTenantId(), user.getId(), PasswordResetTokens.hash("abc"), Instant.now());
        user.deactivate();
        when(tokens.findByTokenHash(PasswordResetTokens.hash("abc"))).thenReturn(Optional.of(token));
        when(staffUsers.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> confirmUseCase().execute("abc", "yeniSifre123"))
            .isInstanceOf(PasswordResetTokenInvalidException.class);
        assertThat(user.getPasswordHash()).isEqualTo("eski-hash");
    }
}
