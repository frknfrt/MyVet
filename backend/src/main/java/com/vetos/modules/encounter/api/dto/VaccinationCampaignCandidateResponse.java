package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.VaccinationCampaignCandidate;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationCampaignCandidateResponse(
    UUID ownerId, String ownerFullName, String ownerPhone, boolean smsConsent, boolean whatsappConsent,
    String patientName, String vaccineName, LocalDate nextDueDate
) {
    public static VaccinationCampaignCandidateResponse from(VaccinationCampaignCandidate c) {
        return new VaccinationCampaignCandidateResponse(
            c.ownerId(), c.ownerFullName(), c.ownerPhone(), c.smsConsent(), c.whatsappConsent(),
            c.patientName(), c.vaccineName(), c.nextDueDate()
        );
    }
}
