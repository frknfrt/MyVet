package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientPhoto;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientPhotoFormat;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.modules.patient.domain.exception.PatientPhotoNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetPatientPhotoUseCase {

    private final PatientRepository patientRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public PatientPhoto execute(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        String ref = patient.getPhotoStorageRef();
        if (ref == null) {
            throw new PatientPhotoNotFoundException(patientId);
        }
        byte[] content = fileStoragePort.retrieve(ref);
        String contentType = PatientPhotoFormat.detectContentType(content).orElse("application/octet-stream");
        return new PatientPhoto(content, contentType);
    }
}
