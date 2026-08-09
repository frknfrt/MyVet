package com.vetos.modules.appointment.api.dto;

import com.vetos.modules.appointment.application.dto.AppointmentCampaignCandidate;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCampaignCandidateResponse(
    UUID ownerId, String ownerFullName, String ownerPhone, boolean smsConsent, boolean whatsappConsent,
    String patientName, Instant scheduledStart
) {
    public static AppointmentCampaignCandidateResponse from(AppointmentCampaignCandidate c) {
        return new AppointmentCampaignCandidateResponse(
            c.ownerId(), c.ownerFullName(), c.ownerPhone(), c.smsConsent(), c.whatsappConsent(), c.patientName(), c.scheduledStart()
        );
    }
}
