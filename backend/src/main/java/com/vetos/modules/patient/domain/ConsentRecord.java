package com.vetos.modules.patient.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false)
    private ConsentType consentType;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static ConsentRecord grant(UUID tenantId, UUID ownerId, ConsentType consentType, String ipAddress) {
        ConsentRecord record = new ConsentRecord();
        record.tenantId = tenantId;
        record.ownerId = ownerId;
        record.consentType = consentType;
        record.granted = true;
        record.ipAddress = ipAddress;
        record.grantedAt = Instant.now();
        return record;
    }

    public void revoke() {
        this.granted = false;
        this.revokedAt = Instant.now();
    }
}
