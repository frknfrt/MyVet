package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.EncounterInventoryUsage;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class EncounterInventoryUsageRepositoryAdapter implements EncounterInventoryUsageRepository {

    private final EncounterInventoryUsageJpaRepository jpaRepository;

    @Override
    public EncounterInventoryUsage save(EncounterInventoryUsage usage) { return jpaRepository.save(usage); }

    @Override
    public List<EncounterInventoryUsage> findByEncounterId(UUID encounterId) { return jpaRepository.findByEncounterId(encounterId); }
}
