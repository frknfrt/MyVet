package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.UUID;

public interface EncounterInventoryUsageRepository {
    EncounterInventoryUsage save(EncounterInventoryUsage usage);
    List<EncounterInventoryUsage> findByEncounterId(UUID encounterId);
}
