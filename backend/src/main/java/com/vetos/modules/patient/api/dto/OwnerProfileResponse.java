package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.OwnerProfile;
import com.vetos.modules.patient.domain.PatientStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OwnerProfileResponse(
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
    List<PatientItem> patients
) {
    public record PatientItem(UUID id, String name, String speciesName, String breedName, PatientStatus status) {}

    public static OwnerProfileResponse from(OwnerProfile profile) {
        return new OwnerProfileResponse(
            profile.id(), profile.fullName(), profile.middleName(), profile.phone(), profile.secondaryPhone(),
            profile.email(), profile.address(), profile.city(), profile.district(), profile.occupation(),
            profile.referralSource(), profile.clientDiscount(), profile.criticalAlert(), profile.notes(),
            profile.marketingConsent(), profile.smsConsent(), profile.whatsappConsent(), profile.notificationConsent(),
            profile.protocolNumber(), profile.birthDate(), profile.nationalId(),
            profile.patients().stream()
                .map(p -> new PatientItem(p.id(), p.name(), p.speciesName(), p.breedName(), p.status()))
                .toList()
        );
    }
}
