package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.PatientStatus;

import java.util.UUID;

public record PatientSearchResult(
    UUID patientId,
    String patientName,
    String speciesName,
    String breedName,
    PatientStatus status,
    UUID ownerId,
    String ownerFullName,
    String ownerPhone
) {}
