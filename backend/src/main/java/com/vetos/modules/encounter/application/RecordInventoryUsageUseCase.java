package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.EncounterInventoryUsage;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordInventoryUsageUseCase {

    private final EncounterInventoryUsageRepository encounterInventoryUsageRepository;
    private final EncounterRepository encounterRepository;

    @Transactional
    public UUID execute(UUID encounterId, UUID inventoryItemId, java.math.BigDecimal quantity) {
        encounterRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId))
            .rejectIfCancelled();
        return encounterInventoryUsageRepository.save(
            EncounterInventoryUsage.record(TenantContext.current(), encounterId, inventoryItemId, quantity)
        ).getId();
    }
}
