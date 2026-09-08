package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.*;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class EncounterLookupAdapter implements EncounterLookupPort {

    private final EncounterJpaRepository jpaRepository;

    @Override
    public EncounterSummary findSummaryById(UUID encounterId) {
        Encounter e = jpaRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        return new EncounterSummary(e.getId(), e.getPatientId(), e.getStaffUserId(), e.getStatus());
    }

    @Override
    public EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit) {
        Encounter current = jpaRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));

        List<PastEncounterSummary> history = jpaRepository.findByPatientId(current.getPatientId()).stream()
            .filter(e -> !e.getId().equals(encounterId))
            .filter(e -> e.getStatus() == EncounterStatus.FINALIZED || e.getStatus() == EncounterStatus.AMENDED)
            .sorted(Comparator.comparing(Encounter::getEncounterDate).reversed())
            .limit(historyLimit)
            .map(e -> new PastEncounterSummary(e.getEncounterDate(), e.getAssessment(), e.getPlan()))
            .toList();

        return new EncounterClinicalContext(current.getId(), current.getPatientId(), current.getAssessment(), history);
    }
}
