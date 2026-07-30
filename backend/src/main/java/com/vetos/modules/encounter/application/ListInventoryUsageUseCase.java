package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.InventoryUsageSummary;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListInventoryUsageUseCase {

    private final EncounterInventoryUsageRepository encounterInventoryUsageRepository;

    @Transactional(readOnly = true)
    public List<InventoryUsageSummary> execute(UUID encounterId) {
        return encounterInventoryUsageRepository.findByEncounterId(encounterId).stream()
            .map(u -> new InventoryUsageSummary(u.getId(), u.getInventoryItemId(), u.getQuantity()))
            .toList();
    }
}
