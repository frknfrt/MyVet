package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.UUID;

/** integration/tarbil modulunun asi aktarimi icin ihtiyac duydugu, salt-okunur gorunum. */
public record VaccinationTarbilView(
    UUID id, UUID tenantId, UUID patientId, String vaccineName, String lotNumber,
    LocalDate administeredDate, VaccinationStatus status
) {}
