package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record UpdateOwnerRequest(
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
    String protocolNumber
) {}
