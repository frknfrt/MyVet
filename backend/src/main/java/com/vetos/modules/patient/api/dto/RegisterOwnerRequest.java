package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterOwnerRequest(
    @NotBlank String fullName,
    String middleName,
    @NotBlank String phone,
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
    @NotNull LocalDate birthDate,
    String nationalId
) {}
