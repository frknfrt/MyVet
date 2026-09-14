package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.PatientStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OwnerProfile(
    UUID id,
    String fullName,
    String middleName,
    String phone,
    String secondaryPhone,
    String email,
    String address,
    String city,
    String district,
    String occupation,
    String referralSource,
    BigDecimal clientDiscount,
    String criticalAlert,
    String notes,
    boolean marketingConsent,
    boolean smsConsent,
    boolean whatsappConsent,
    boolean notificationConsent,
    String protocolNumber,
    LocalDate birthDate,
    String nationalId,
    List<PatientSummaryItem> patients
) {
    public record PatientSummaryItem(UUID id, String name, String speciesName, String breedName, PatientStatus status) {}
}
