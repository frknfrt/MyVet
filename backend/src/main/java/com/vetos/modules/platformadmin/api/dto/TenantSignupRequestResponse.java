package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantSignupRequestResponse(
    UUID id, String clinicName, String adminFullName, String adminEmail, String phone,
    String planCode, TenantSignupRequestStatus status, Instant createdAt
) {
    public static TenantSignupRequestResponse from(TenantSignupRequest r) {
        return new TenantSignupRequestResponse(
            r.getId(), r.getClinicName(), r.getAdminFullName(), r.getAdminEmail(), r.getPhone(),
            r.getPlanCode(), r.getStatus(), r.getCreatedAt()
        );
    }
}
