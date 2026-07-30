package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.OwnerProfile;
import com.vetos.modules.patient.domain.*;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetOwnerProfileUseCase {

    private final OwnerRepository ownerRepository;
    private final PatientRepository patientRepository;
    private final SpeciesRepository speciesRepository;

    @Transactional(readOnly = true)
    public OwnerProfile execute(UUID ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
            .orElseThrow(() -> new OwnerNotFoundException(ownerId));

        var patients = patientRepository.findByOwnerId(ownerId).stream()
            .map(patient -> new OwnerProfile.PatientSummaryItem(
                patient.getId(), patient.getName(), speciesNameOf(patient.getSpeciesId())
            ))
            .toList();

        return new OwnerProfile(
            owner.getId(), owner.getFullName(), owner.getPhone(), owner.getEmail(),
            owner.getAddress(), owner.isMarketingConsent(), patients
        );
    }

    private String speciesNameOf(UUID speciesId) {
        return speciesRepository.findById(speciesId).map(Species::getName).orElse(null);
    }
}
