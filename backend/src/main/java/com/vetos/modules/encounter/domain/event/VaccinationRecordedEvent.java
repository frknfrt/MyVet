package com.vetos.modules.encounter.domain.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Asi uygulandi (ADMINISTERED). inventoryItemId: stoktan secildiyse kalem, aksi halde null; lotNumber: kayittaki seri
 * (inventory dusmeden once kalemin serisiyle karsilastirir) (spec 2026-10-04 P2).
 */
public record VaccinationRecordedEvent(UUID vaccinationRecordId, UUID patientId, String vaccineName, LocalDate administeredDate,
                                       UUID inventoryItemId, String lotNumber) {}
