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
    private final DrugAllergyGuard drugAllergyGuard;

    @Transactional
    public UUID execute(UUID encounterId, UUID inventoryItemId, java.math.BigDecimal quantity) {
        return execute(encounterId, inventoryItemId, quantity, null);
    }

    @Transactional
    public UUID execute(UUID encounterId, UUID inventoryItemId, java.math.BigDecimal quantity,
                        com.vetos.modules.encounter.application.dto.AllergyAcknowledgement allergy) {
        var encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        encounter.rejectIfCancelled();
        drugAllergyGuard.enforce(encounter.getPatientId(), drugAllergyGuard.labels(java.util.List.of(), java.util.List.of(inventoryItemId)), allergy);
        return encounterInventoryUsageRepository.save(
            EncounterInventoryUsage.record(TenantContext.current(), encounterId, inventoryItemId, quantity)
        ).getId();
    }
}
