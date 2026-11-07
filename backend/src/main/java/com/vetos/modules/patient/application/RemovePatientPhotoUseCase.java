package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RemovePatientPhotoUseCase {

    private final PatientRepository patientRepository;
    private final FileStoragePort fileStoragePort;

    /** Fotograf yoksa no-op. */
    @Transactional
    public void execute(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        String ref = patient.getPhotoStorageRef();
        if (ref == null) {
            return;
        }
        fileStoragePort.delete(ref);
        patient.clearPhoto();
        patientRepository.save(patient);
    }
}
