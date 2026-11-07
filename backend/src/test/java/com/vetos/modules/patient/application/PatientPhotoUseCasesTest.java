package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientPhoto;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.modules.patient.domain.exception.PatientPhotoNotFoundException;
import com.vetos.modules.patient.domain.exception.UnsupportedPatientPhotoException;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientPhotoUseCasesTest {

    @Mock private PatientRepository patientRepository;
    @Mock private FileStoragePort fileStoragePort;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private Patient patientFound(UUID id) {
        Patient p = Patient.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Pamuk", Sex.FEMALE, null);
        when(patientRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private UploadPatientPhotoUseCase upload() {
        return new UploadPatientPhotoUseCase(patientRepository, fileStoragePort);
    }

    @Test
    void should_storeJpeg_and_setRefOnPatient() {
        UUID id = UUID.randomUUID();
        Patient p = patientFound(id);
        when(fileStoragePort.store(JPEG, "image/jpeg")).thenReturn("new-ref");

        String ref = upload().execute(id, JPEG);

        assertThat(ref).isEqualTo("new-ref");
        assertThat(p.getPhotoStorageRef()).isEqualTo("new-ref");
        verify(patientRepository).save(p);
        verify(fileStoragePort, never()).delete(anyString());
    }

    @Test
    void should_deleteOldPhoto_whenReplacing() {
        UUID id = UUID.randomUUID();
        Patient p = patientFound(id);
        p.replacePhoto("old");
        when(fileStoragePort.store(JPEG, "image/jpeg")).thenReturn("new-ref");

        upload().execute(id, JPEG);

        verify(fileStoragePort).delete("old");
        assertThat(p.getPhotoStorageRef()).isEqualTo("new-ref");
    }

    @Test
    void should_rejectNonImage() {
        UUID id = UUID.randomUUID();
        patientFound(id);

        assertThatThrownBy(() -> upload().execute(id, "%PDF-1.7".getBytes()))
            .isInstanceOf(UnsupportedPatientPhotoException.class);
        verify(fileStoragePort, never()).store(any(), anyString());
    }

    @Test
    void should_rejectOver2MB() {
        UUID id = UUID.randomUUID();
        patientFound(id);
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;

        assertThatThrownBy(() -> upload().execute(id, big))
            .isInstanceOf(UnsupportedPatientPhotoException.class);
        verify(fileStoragePort, never()).store(any(), anyString());
    }

    @Test
    void should_throwNotFound_whenPatientMissing() {
        UUID id = UUID.randomUUID();
        when(patientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> upload().execute(id, JPEG)).isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void remove_should_deleteAndClear() {
        UUID id = UUID.randomUUID();
        Patient p = patientFound(id);
        p.replacePhoto("ref");

        new RemovePatientPhotoUseCase(patientRepository, fileStoragePort).execute(id);

        verify(fileStoragePort).delete("ref");
        assertThat(p.getPhotoStorageRef()).isNull();
        verify(patientRepository).save(p);
    }

    @Test
    void remove_should_beNoOp_whenNoPhoto() {
        UUID id = UUID.randomUUID();
        patientFound(id);

        new RemovePatientPhotoUseCase(patientRepository, fileStoragePort).execute(id);

        verify(fileStoragePort, never()).delete(anyString());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void get_should_returnBytesAndDetectedType() {
        UUID id = UUID.randomUUID();
        Patient p = patientFound(id);
        p.replacePhoto("ref");
        when(fileStoragePort.retrieve("ref")).thenReturn(JPEG);

        PatientPhoto photo = new GetPatientPhotoUseCase(patientRepository, fileStoragePort).execute(id);

        assertThat(photo.content()).isEqualTo(JPEG);
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void get_should_throwPhotoNotFound_whenNoPhoto() {
        UUID id = UUID.randomUUID();
        patientFound(id);

        assertThatThrownBy(() -> new GetPatientPhotoUseCase(patientRepository, fileStoragePort).execute(id))
            .isInstanceOf(PatientPhotoNotFoundException.class);
    }
}
