package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * vetly.com'da bir ziyaretcinin odeme baslatmasi ile tenant'in gercekten
 * olusturulmasi arasindaki bekleme durumunu tutar -- odeme aninda henuz
 * ne bir Tenant ne bir PlatformInvoice var, bu yuzden iyzico'nun
 * conversationId'si bu nesnenin id'sine baglanir.
 */
@Entity
@Table(name = "tenant_signup_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantSignupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clinic_name", nullable = false)
    private String clinicName;

    @Column(name = "admin_full_name", nullable = false)
    private String adminFullName;

    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    @Column
    private String phone;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantSignupRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TenantSignupRequest create(
        String clinicName, String adminFullName, String adminEmail, String phone, String planCode
    ) {
        TenantSignupRequest request = new TenantSignupRequest();
        request.clinicName = clinicName;
        request.adminFullName = adminFullName;
        request.adminEmail = adminEmail;
        request.phone = phone;
        request.planCode = planCode;
        request.status = TenantSignupRequestStatus.PENDING;
        request.createdAt = Instant.now();
        return request;
    }

    public void complete() {
        this.status = TenantSignupRequestStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return status == TenantSignupRequestStatus.COMPLETED;
    }
}
