package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.TrialSignupRequest;

import java.time.Instant;
import java.util.UUID;

/** Gizli alanlar (sifre/kod hash, onay IP) bilerek tasinmaz. */
public record TrialSignupRequestResponse(
    UUID id,
    String fullName,
    String email,
    String phone,
    String clinicName,
    String city,
    String chamberRegistrationNo,
    String status,
    UUID tenantId,
    Instant createdAt,
    Instant completedAt
) {
    public static TrialSignupRequestResponse from(TrialSignupRequest r) {
        return new TrialSignupRequestResponse(
            r.getId(), r.getFullName(), r.getEmail(), r.getPhone(), r.getClinicName(), r.getCity(),
            r.getChamberRegistrationNo(), r.getStatus().name(), r.getTenantId(), r.getCreatedAt(), r.getCompletedAt()
        );
    }
}
