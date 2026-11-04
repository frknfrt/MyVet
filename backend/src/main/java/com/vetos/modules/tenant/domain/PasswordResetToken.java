package com.vetos.modules.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * "Şifremi unuttum" bağlantısı. Token'ın kendisi saklanmaz, yalnız özeti (tokenHash) tutulur.
 * 1 saat geçerli, tek kullanımlık; yeni bir istek açık kalan eskileri geçersiz kılar.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    public static final Duration VALIDITY = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public static PasswordResetToken issue(UUID tenantId, UUID staffUserId, String tokenHash, Instant now) {
        PasswordResetToken t = new PasswordResetToken();
        t.tenantId = tenantId;
        t.staffUserId = staffUserId;
        t.tokenHash = tokenHash;
        t.createdAt = now;
        t.expiresAt = now.plus(VALIDITY);
        return t;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** Kullanıldı ya da yerine yenisi istendi: bir daha kullanılamaz. */
    public void consume(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }
}
