package com.vetos.modules.patient.domain;

import java.util.UUID;

public record OwnerSummary(
    UUID id, String fullName, String phone, String address, String nationalIdMasked,
    boolean smsConsent, boolean whatsappConsent
) {}
