package com.vetos.modules.encounter.domain.event;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationRecordedEvent(UUID vaccinationRecordId, UUID patientId, String vaccineName, LocalDate administeredDate) {}
