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

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

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

    private String specialty;

    private String bio;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static StaffUser register(
        UUID tenantId, UUID branchId, String fullName, String email, String passwordHash, StaffRole role
    ) {
        StaffUser staffUser = new StaffUser();
        staffUser.tenantId = tenantId;
        staffUser.branchId = branchId;
        staffUser.fullName = fullName;
        staffUser.email = email;
        staffUser.passwordHash = passwordHash;
        staffUser.role = role;
        staffUser.twoFactorEnabled = false;
        staffUser.active = true;
        staffUser.createdAt = Instant.now();
        return staffUser;
    }

    public void updateContactInfo(String phone) {
        this.phone = phone;
    }

    public void updateProfile(String fullName, String phone, String licenseNumber, String specialty, String bio) {
        this.fullName = fullName;
        this.phone = phone;
        this.licenseNumber = licenseNumber;
        this.specialty = specialty;
        this.bio = bio;
    }

    public void changeRole(StaffRole newRole) {
        this.role = newRole;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void enableTwoFactor() {
        this.twoFactorEnabled = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
