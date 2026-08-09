package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.OwnerCampaignCandidate;

import java.time.Instant;
import java.util.UUID;

public record OwnerCampaignCandidateResponse(
    UUID id, String fullName, String phone, boolean smsConsent, boolean whatsappConsent, Instant createdAt
) {
    public static OwnerCampaignCandidateResponse from(OwnerCampaignCandidate c) {
        return new OwnerCampaignCandidateResponse(c.id(), c.fullName(), c.phone(), c.smsConsent(), c.whatsappConsent(), c.createdAt());
    }
}
