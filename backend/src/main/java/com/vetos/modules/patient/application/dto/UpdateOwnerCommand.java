package com.vetos.modules.patient.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateOwnerCommand(
    UUID ownerId,
    String fullName,
    String phone,
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
