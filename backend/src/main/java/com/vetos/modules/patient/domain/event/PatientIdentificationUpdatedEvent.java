package com.vetos.modules.patient.domain.event;

import java.util.UUID;

public record PatientIdentificationUpdatedEvent(UUID patientId, String microchipNumber, String tarbilAnimalId) {}
