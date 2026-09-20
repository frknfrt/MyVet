package com.vetos.modules.lab.application;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadLabResultFileUseCaseTest {

    @Mock private LabResultRepository labResultRepository;
    @Mock private LabResultFileRepository labResultFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_storeContentViaPort_and_saveFileWithReturnedRef() {
        UploadLabResultFileUseCase useCase = new UploadLabResultFileUseCase(
            labResultRepository, labResultFileRepository, fileStoragePort
        );
        UUID tenantId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        LabResult result = LabResult.request(tenantId, UUID.randomUUID(), UUID.randomUUID(), "Tam Kan Sayimi", null);
        byte[] content = "dosya-icerigi".getBytes();
        when(labResultRepository.findById(resultId)).thenReturn(Optional.of(result));
        when(fileStoragePort.store(content, "application/pdf")).thenReturn("some-storage-ref");
        when(labResultFileRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(resultId, "sonuc.pdf", "application/pdf", content);

        verify(fileStoragePort).store(content, "application/pdf");
        ArgumentCaptor<LabResultFile> captor = ArgumentCaptor.forClass(LabResultFile.class);
        verify(labResultFileRepository).save(captor.capture());
        assertThat(captor.getValue().getStorageRef()).isEqualTo("some-storage-ref");
        assertThat(captor.getValue().getFileSize()).isEqualTo(content.length);
        assertThat(captor.getValue().getFileName()).isEqualTo("sonuc.pdf");
    }
}
