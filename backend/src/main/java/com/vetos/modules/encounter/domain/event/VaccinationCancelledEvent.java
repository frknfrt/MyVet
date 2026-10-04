package com.vetos.modules.encounter.domain.event;

import java.util.UUID;

/** Asi kaydi iptal edildi; inventory dusulmus adedi geri ekler (spec 2026-10-04 P2 S3.3). */
public record VaccinationCancelledEvent(UUID vaccinationRecordId, UUID inventoryItemId) {}
