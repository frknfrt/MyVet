package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientProfile;
import com.vetos.modules.patient.domain.*;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetPatientProfileUseCase {

    private final PatientRepository patientRepository;
    private final OwnerRepository ownerRepository;
    private final SpeciesRepository speciesRepository;
    private final BreedRepository breedRepository;

    @Transactional(readOnly = true)
    public PatientProfile execute(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        Owner owner = ownerRepository.findById(patient.getOwnerId())
            .orElseThrow(() -> new OwnerNotFoundException(patient.getOwnerId()));

        String speciesName = speciesRepository.findById(patient.getSpeciesId()).map(Species::getName).orElse(null);
        String breedName = patient.getBreedId() == null
            ? null
            : breedRepository.findById(patient.getBreedId()).map(Breed::getName).orElse(null);

        return new PatientProfile(
            patient.getId(), patient.getName(), speciesName, breedName, patient.getSex(), patient.isNeutered(),
            patient.getBirthDate(), patient.getMicrochipNumber(), patient.getTarbilAnimalId(), patient.getWeightKg(),
            patient.getPhotoUrl(), patient.getStatus(), owner.getId(), owner.getFullName(), owner.getPhone()
        );
    }
}
