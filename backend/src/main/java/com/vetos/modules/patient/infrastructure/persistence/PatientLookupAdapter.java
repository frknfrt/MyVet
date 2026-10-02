package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.*;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PatientLookupAdapter implements PatientLookupPort {

    private final PatientJpaRepository jpaRepository;
    private final SpeciesJpaRepository speciesJpaRepository;
    private final BreedJpaRepository breedJpaRepository;

    @Override
    public PatientSummary findSummaryById(UUID patientId) {
        Patient p = jpaRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        String speciesName = speciesJpaRepository.findById(p.getSpeciesId()).map(Species::getName).orElse(null);
        return new PatientSummary(p.getId(), p.getName(), p.getOwnerId(), speciesName);
    }

    @Override
    public Optional<PatientTarbilProfile> findTarbilProfile(UUID patientId) {
        return jpaRepository.findById(patientId).map(p -> new PatientTarbilProfile(
            p.getId(), p.getName(), p.getMicrochipNumber(), p.getSpeciesId(),
            speciesJpaRepository.findById(p.getSpeciesId()).map(Species::getName).orElse(null),
            p.getBreedId() == null ? null : breedJpaRepository.findById(p.getBreedId()).map(Breed::getName).orElse(null),
            p.getSex(), p.getBirthDate()
        ));
    }
}
