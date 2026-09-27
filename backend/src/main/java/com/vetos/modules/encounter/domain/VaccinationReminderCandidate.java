package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationReminderCandidate(UUID recordId, UUID patientId, String vaccineName, LocalDate nextDueDate) {}
