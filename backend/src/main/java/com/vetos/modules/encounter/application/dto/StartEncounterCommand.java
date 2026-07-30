package com.vetos.modules.encounter.application.dto;

import java.util.UUID;

public record StartEncounterCommand(UUID patientId, UUID staffUserId, UUID appointmentId, String templateUsed) {}
