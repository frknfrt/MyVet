package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.EncounterSummary;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
