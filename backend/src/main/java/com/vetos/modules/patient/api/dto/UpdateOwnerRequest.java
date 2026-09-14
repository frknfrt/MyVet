package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateOwnerRequest(
    @NotBlank String fullName,
    @NotBlank String phone,
    String email,
    String address,
    String middleName,
    String secondaryPhone,
    String city,
    String district,
    String occupation,
    String referralSource,
    BigDecimal clientDiscount,
    String criticalAlert,
    String notes,
    boolean smsConsent,
    boolean whatsappConsent,
    boolean notificationConsent,
    String protocolNumber,
    @NotNull LocalDate birthDate,
    String nationalId
) {}
