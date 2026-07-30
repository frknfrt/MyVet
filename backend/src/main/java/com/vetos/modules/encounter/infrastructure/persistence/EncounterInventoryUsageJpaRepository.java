package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.EncounterInventoryUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface EncounterInventoryUsageJpaRepository extends JpaRepository<EncounterInventoryUsage, UUID> {
    List<EncounterInventoryUsage> findByEncounterId(UUID encounterId);
}
