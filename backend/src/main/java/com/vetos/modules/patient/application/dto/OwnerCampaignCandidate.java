package com.vetos.modules.patient.application.dto;

import java.time.Instant;
import java.util.UUID;

public record OwnerCampaignCandidate(
    UUID id, String fullName, String phone, boolean smsConsent, boolean whatsappConsent, Instant createdAt
) {}
