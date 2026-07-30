package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.*;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class PatientLookupAdapter implements PatientLookupPort {

    private final PatientJpaRepository jpaRepository;
    private final SpeciesJpaRepository speciesJpaRepository;

    @Override
    public PatientSummary findSummaryById(UUID patientId) {
        Patient p = jpaRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        String speciesName = speciesJpaRepository.findById(p.getSpeciesId()).map(Species::getName).orElse(null);
        return new PatientSummary(p.getId(), p.getName(), p.getOwnerId(), speciesName);
    }
}
