package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.RegisterPatientCommand;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.event.PatientRegisteredEvent;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterPatientUseCase {

    private final PatientRepository patientRepository;
    private final OwnerRepository ownerRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public UUID execute(RegisterPatientCommand command) {
        Owner owner = ownerRepository.findById(command.ownerId())
            .orElseThrow(() -> new OwnerNotFoundException(command.ownerId()));

        Patient patient = Patient.register(
            owner.getId(), command.speciesId(), command.breedId(), command.name(), command.sex(), command.birthDate()
        );
        patient.updateDetails(
            command.color(), command.temperament(), command.distinguishingMarks(), command.aggressive(),
            command.bloodType(), command.foodBrand(), command.criticalAlert(), command.notes(),
            command.protocolNumber(), command.rabiesTag()
        );
        patientRepository.save(patient);
        eventPublisher.publish(new PatientRegisteredEvent(patient.getId(), owner.getId()));
        return patient.getId();
    }
}
