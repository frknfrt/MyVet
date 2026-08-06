package com.vetos.modules.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Column(name = "license_number")
    private String licenseNumber;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static StaffUser register(UUID branchId, String fullName, String email, String passwordHash, StaffRole role) {
        StaffUser staffUser = new StaffUser();
        staffUser.branchId = branchId;
        staffUser.fullName = fullName;
        staffUser.email = email;
        staffUser.passwordHash = passwordHash;
        staffUser.role = role;
        staffUser.twoFactorEnabled = false;
        staffUser.createdAt = Instant.now();
        return staffUser;
    }

    public void updateContactInfo(String phone) {
        this.phone = phone;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void enableTwoFactor() {
        this.twoFactorEnabled = true;
    }
}
