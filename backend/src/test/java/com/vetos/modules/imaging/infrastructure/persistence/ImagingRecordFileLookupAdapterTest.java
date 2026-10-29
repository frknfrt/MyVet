package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileForOcr;
import com.vetos.modules.imaging.domain.ImagingRecordFileLookupPort;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagingRecordFileLookupAdapterTest {

    @Mock private ImagingRecordFileRepository imagingRecordFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    private ImagingRecordFileLookupPort adapter() {
        return new ImagingRecordFileLookupAdapter(imagingRecordFileRepository, fileStoragePort);
    }

    @Test
    void findFiles_returnsContentForMatchingIds_inRequestedOrder() {
        UUID imagingRecordId = UUID.randomUUID();
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        ImagingRecordFile file1 = ImagingRecordFile.create(UUID.randomUUID(), imagingRecordId, "rapor1.jpg", "image/jpeg", 10L, "ref-1");
        ImagingRecordFile file2 = ImagingRecordFile.create(UUID.randomUUID(), imagingRecordId, "rapor2.jpg", "image/jpeg", 20L, "ref-2");
        when(imagingRecordFileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(imagingRecordFileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        when(fileStoragePort.retrieve("ref-1")).thenReturn("icerik-1".getBytes());
        when(fileStoragePort.retrieve("ref-2")).thenReturn("icerik-2".getBytes());

        List<ImagingRecordFileForOcr> result = adapter().findFiles(imagingRecordId, List.of(fileId2, fileId1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fileName()).isEqualTo("rapor2.jpg");
        assertThat(result.get(0).content()).isEqualTo("icerik-2".getBytes());
        assertThat(result.get(1).fileName()).isEqualTo("rapor1.jpg");
    }

    @Test
    void findFiles_rejectsFileFromDifferentImagingRecord() {
        UUID imagingRecordId = UUID.randomUUID();
        UUID otherRecordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ImagingRecordFile file = ImagingRecordFile.create(UUID.randomUUID(), otherRecordId, "baska.jpg", "image/jpeg", 10L, "ref-x");
        when(imagingRecordFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> adapter().findFiles(imagingRecordId, List.of(fileId)))
            .isInstanceOf(ImagingRecordNotFoundException.class);
    }

    @Test
    void findFiles_rejectsUnknownFileId() {
        UUID imagingRecordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(imagingRecordFileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter().findFiles(imagingRecordId, List.of(fileId)))
            .isInstanceOf(ImagingRecordNotFoundException.class);
    }
}
