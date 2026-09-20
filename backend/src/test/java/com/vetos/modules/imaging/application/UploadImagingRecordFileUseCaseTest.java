package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
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
class UploadImagingRecordFileUseCaseTest {

    @Mock private ImagingRecordRepository imagingRecordRepository;
    @Mock private ImagingRecordFileRepository imagingRecordFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_storeContentViaPort_and_saveFileWithReturnedRef() {
        UploadImagingRecordFileUseCase useCase = new UploadImagingRecordFileUseCase(
            imagingRecordRepository, imagingRecordFileRepository, fileStoragePort
        );
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        ImagingRecord record = ImagingRecord.request(tenantId, UUID.randomUUID(), UUID.randomUUID(), ImagingModality.XRAY, null, null);
        byte[] content = "dosya-icerigi".getBytes();
        when(imagingRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(fileStoragePort.store(content, "image/png")).thenReturn("some-storage-ref");
        when(imagingRecordFileRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(recordId, "xray.png", "image/png", content);

        verify(fileStoragePort).store(content, "image/png");
        ArgumentCaptor<ImagingRecordFile> captor = ArgumentCaptor.forClass(ImagingRecordFile.class);
        verify(imagingRecordFileRepository).save(captor.capture());
        assertThat(captor.getValue().getStorageRef()).isEqualTo("some-storage-ref");
        assertThat(captor.getValue().getFileSize()).isEqualTo(content.length);
        assertThat(captor.getValue().getFileName()).isEqualTo("xray.png");
    }
}
