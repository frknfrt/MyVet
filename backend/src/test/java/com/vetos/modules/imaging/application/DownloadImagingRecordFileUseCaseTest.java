package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordFileContent;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
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
class DownloadImagingRecordFileUseCaseTest {

    @Mock private ImagingRecordFileRepository imagingRecordFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_retrieveContentViaPort_using_fileStorageRef() {
        DownloadImagingRecordFileUseCase useCase = new DownloadImagingRecordFileUseCase(imagingRecordFileRepository, fileStoragePort);
        UUID fileId = UUID.randomUUID();
        ImagingRecordFile file = ImagingRecordFile.create(
            UUID.randomUUID(), UUID.randomUUID(), "xray.png", "image/png", 123L, "some-storage-ref"
        );
        byte[] content = "dosya-icerigi".getBytes();
        when(imagingRecordFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileStoragePort.retrieve("some-storage-ref")).thenReturn(content);

        ImagingRecordFileContent result = useCase.execute(fileId);

        assertThat(result).isEqualTo(new ImagingRecordFileContent("xray.png", "image/png", content));
    }
}
