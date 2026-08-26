package com.vetos.modules.tenant.domain;

import com.vetos.modules.tenant.domain.exception.StaffInviteInvalidStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "staff_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffInvite {

    private static final long EXPIRY_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffInviteStatus status;

    @Column(name = "invited_by_staff_user_id", nullable = false)
    private UUID invitedByStaffUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public static StaffInvite create(
        UUID tenantId, UUID branchId, String email, String fullName, StaffRole role, UUID invitedByStaffUserId
    ) {
        StaffInvite invite = new StaffInvite();
        invite.tenantId = tenantId;
        invite.branchId = branchId;
        invite.email = email;
        invite.fullName = fullName;
        invite.role = role;
        invite.token = UUID.randomUUID().toString();
        invite.status = StaffInviteStatus.PENDING;
        invite.invitedByStaffUserId = invitedByStaffUserId;
        invite.createdAt = Instant.now();
        invite.expiresAt = invite.createdAt.plus(EXPIRY_DAYS, ChronoUnit.DAYS);
        return invite;
    }

    public boolean isExpired() {
        return status == StaffInviteStatus.PENDING && Instant.now().isAfter(expiresAt);
    }

    public void assertAcceptable() {
        if (isExpired()) {
            throw new StaffInviteInvalidStateException(id, "Davetin suresi dolmus");
        }
        if (status != StaffInviteStatus.PENDING) {
            throw new StaffInviteInvalidStateException(id, "Davet artik gecerli degil: " + status);
        }
    }

    public void accept() {
        assertAcceptable();
        this.status = StaffInviteStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    public void revoke() {
        if (status != StaffInviteStatus.PENDING) {
            throw new StaffInviteInvalidStateException(id, "Sadece bekleyen bir davet iptal edilebilir");
        }
        this.status = StaffInviteStatus.REVOKED;
    }
}
