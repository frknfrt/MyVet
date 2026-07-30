package com.vetos.modules.patient.application.dto;

import java.util.List;
import java.util.UUID;

public record OwnerProfile(
    UUID id,
    String fullName,
    String phone,
    String email,
    String address,
    boolean marketingConsent,
    List<PatientSummaryItem> patients
) {
    public record PatientSummaryItem(UUID id, String name, String speciesName) {}
}
