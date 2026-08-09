package com.vetos.modules.appointment.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCampaignCandidate(
    UUID ownerId, String ownerFullName, String ownerPhone, boolean smsConsent, boolean whatsappConsent,
    String patientName, Instant scheduledStart
) {}
