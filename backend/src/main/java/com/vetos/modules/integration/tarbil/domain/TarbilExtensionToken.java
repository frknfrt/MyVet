package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Hekime ozel TARBIL eklentisi anahtari. Yasam dongusu: issuePairing (kod) -> pair (anahtar)
 * -> revoke. Kod ve anahtar yalnizca SHA-256 ozeti olarak saklanir.
 * @TenantId DISINDA: kimlik dogrulama asamasinda kiraci henuz bilinmiyor.
 */
@Entity
@Table(name = "tarbil_extension_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilExtensionToken {

    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    private String label;

    @Column(name = "token_hash", unique = true)
    private String tokenHash;

    @Column(name = "pairing_code_hash", unique = true)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at", nullable = false)
    private Instant pairingExpiresAt;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static TarbilExtensionToken issuePairing(UUID tenantId, UUID staffId, String codeHash, Instant expiresAt, Instant now) {
        TarbilExtensionToken t = new TarbilExtensionToken();
        t.tenantId = tenantId;
        t.staffUserId = staffId;
        t.pairingCodeHash = codeHash;
        t.pairingExpiresAt = expiresAt;
        t.createdAt = now;
        return t;
    }

    public void pair(String tokenHash, String label, Instant now) {
        if (pairedAt != null || revokedAt != null || pairingCodeHash == null || now.isAfter(pairingExpiresAt)) {
            throw new InvalidPairingCodeUnauthorizedException();
        }
        this.tokenHash = tokenHash;
        this.label = label;
        this.pairedAt = now;
        this.pairingCodeHash = null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.pairingCodeHash = null;
        }
    }

    public boolean isUsable() {
        return pairedAt != null && revokedAt == null && tokenHash != null;
    }

    public void touch(Instant now) {
        if (lastUsedAt == null || Duration.between(lastUsedAt, now).compareTo(TOUCH_INTERVAL) > 0) {
            this.lastUsedAt = now;
        }
    }
}
