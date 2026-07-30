package com.vetos.modules.patient.domain.event;

import java.util.UUID;

public record PatientRegisteredEvent(UUID patientId, UUID ownerId) {}
