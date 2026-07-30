package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientSearchResult;
import com.vetos.modules.patient.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @docs/implementation-plan.md Modul 2: "hasta+sahip birlesik arama" -- tek
 * bir arama kutusu hem hasta adinda hem sahip adinda/telefonunda eslesir.
 */
@Service
@RequiredArgsConstructor
public class SearchPatientsUseCase {

    private final PatientRepository patientRepository;
    private final OwnerRepository ownerRepository;
    private final SpeciesRepository speciesRepository;
    private final BreedRepository breedRepository;

    @Transactional(readOnly = true)
    public List<PatientSearchResult> execute(UUID tenantId, String query) {
        return patientRepository.searchByNameOrOwner(tenantId, query).stream()
            .map(this::toResult)
            .toList();
    }

    private PatientSearchResult toResult(Patient patient) {
        Owner owner = ownerRepository.findById(patient.getOwnerId()).orElse(null);
        String speciesName = speciesRepository.findById(patient.getSpeciesId()).map(Species::getName).orElse(null);
        String breedName = patient.getBreedId() == null
            ? null
            : breedRepository.findById(patient.getBreedId()).map(Breed::getName).orElse(null);

        return new PatientSearchResult(
            patient.getId(), patient.getName(), speciesName, breedName, patient.getStatus(),
            owner != null ? owner.getId() : null,
            owner != null ? owner.getFullName() : null,
            owner != null ? owner.getPhone() : null
        );
    }
}
