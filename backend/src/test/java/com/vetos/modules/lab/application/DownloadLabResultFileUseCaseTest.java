package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultFileContent;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadLabResultFileUseCaseTest {

    @Mock private LabResultFileRepository labResultFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_retrieveContentViaPort_using_fileStorageRef() {
        DownloadLabResultFileUseCase useCase = new DownloadLabResultFileUseCase(labResultFileRepository, fileStoragePort);
        UUID fileId = UUID.randomUUID();
        LabResultFile file = LabResultFile.create(
            UUID.randomUUID(), UUID.randomUUID(), "sonuc.pdf", "application/pdf", 456L, "some-storage-ref"
        );
        byte[] content = "dosya-icerigi".getBytes();
        when(labResultFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileStoragePort.retrieve("some-storage-ref")).thenReturn(content);

        LabResultFileContent result = useCase.execute(fileId);

        assertThat(result).isEqualTo(new LabResultFileContent("sonuc.pdf", "application/pdf", content));
    }
}
