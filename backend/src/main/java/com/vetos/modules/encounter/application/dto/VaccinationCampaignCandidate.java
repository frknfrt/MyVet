package com.vetos.modules.encounter.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationCampaignCandidate(
    UUID ownerId, String ownerFullName, String ownerPhone, boolean smsConsent, boolean whatsappConsent,
    String patientName, String vaccineName, LocalDate nextDueDate
) {}
