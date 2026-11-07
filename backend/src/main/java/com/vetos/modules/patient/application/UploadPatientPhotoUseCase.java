package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientPhotoFormat;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.modules.patient.domain.exception.UnsupportedPatientPhotoException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadPatientPhotoUseCase {

    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final PatientRepository patientRepository;
    private final FileStoragePort fileStoragePort;

    /** Yeni storageRef'i doner. */
    @Transactional
    public String execute(UUID patientId, byte[] content) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        if (content == null || content.length > MAX_BYTES) {
            throw new UnsupportedPatientPhotoException();
        }
        String contentType = PatientPhotoFormat.detectContentType(content)
            .orElseThrow(UnsupportedPatientPhotoException::new);

        String newRef = fileStoragePort.store(content, contentType);
        String oldRef = patient.getPhotoStorageRef();
        if (oldRef != null) {
            fileStoragePort.delete(oldRef);
        }
        patient.replacePhoto(newRef);
        patientRepository.save(patient);
        return newRef;
    }
}
