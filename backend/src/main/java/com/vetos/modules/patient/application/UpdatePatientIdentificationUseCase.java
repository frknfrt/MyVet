package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.UpdatePatientIdentificationCommand;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.event.PatientIdentificationUpdatedEvent;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePatientIdentificationUseCase {

    private final PatientRepository patientRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UpdatePatientIdentificationCommand command) {
        Patient patient = patientRepository.findById(command.patientId())
            .orElseThrow(() -> new PatientNotFoundException(command.patientId()));

        patient.updateMicrochip(command.microchipNumber());
        patient.updateTarbilAnimalId(command.tarbilAnimalId());
        patientRepository.save(patient);

        eventPublisher.publish(new PatientIdentificationUpdatedEvent(
            patient.getId(), command.microchipNumber(), command.tarbilAnimalId()
        ));
    }
}
