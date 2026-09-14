package com.vetos.modules.patient.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterOwnerCommand(
    UUID tenantId,
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
    String nationalId
) {}
