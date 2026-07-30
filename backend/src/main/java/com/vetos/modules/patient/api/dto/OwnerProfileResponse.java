package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.OwnerProfile;

import java.util.List;
import java.util.UUID;

public record OwnerProfileResponse(
    UUID id,
    String fullName,
    String phone,
    String email,
    String address,
    boolean marketingConsent,
    List<PatientItem> patients
) {
    public record PatientItem(UUID id, String name, String speciesName) {}

    public static OwnerProfileResponse from(OwnerProfile profile) {
        return new OwnerProfileResponse(
            profile.id(), profile.fullName(), profile.phone(), profile.email(),
            profile.address(), profile.marketingConsent(),
            profile.patients().stream().map(p -> new PatientItem(p.id(), p.name(), p.speciesName())).toList()
        );
    }
}
