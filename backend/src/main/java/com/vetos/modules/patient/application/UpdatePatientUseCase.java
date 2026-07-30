package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.UpdatePatientCommand;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePatientUseCase {

    private final PatientRepository patientRepository;

    @Transactional
    public void execute(UpdatePatientCommand command) {
        Patient patient = patientRepository.findById(command.patientId())
            .orElseThrow(() -> new PatientNotFoundException(command.patientId()));

        patient.updateProfile(command.name(), command.breedId(), command.sex(), command.birthDate(), command.neutered());
        patientRepository.save(patient);
    }
}
