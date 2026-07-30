package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.event.EncounterFinalizedEvent;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalizeEncounterUseCase {

    private final EncounterRepository encounterRepository;
    private final EncounterInventoryUsageRepository encounterInventoryUsageRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID encounterId) {
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        encounter.finalizeEncounter();
        encounterRepository.save(encounter);

        var usedItems = encounterInventoryUsageRepository.findByEncounterId(encounterId).stream()
            .map(u -> new EncounterFinalizedEvent.UsedItem(u.getInventoryItemId(), u.getQuantity()))
            .toList();

        eventPublisher.publish(new EncounterFinalizedEvent(
            encounter.getId(), encounter.getPatientId(), encounter.getStaffUserId(), usedItems
        ));
    }
}
