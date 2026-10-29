package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileForOcr;
import com.vetos.modules.lab.domain.LabResultFileLookupPort;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
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
class LabResultFileLookupAdapterTest {

    @Mock private LabResultFileRepository labResultFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    private LabResultFileLookupPort adapter() {
        return new LabResultFileLookupAdapter(labResultFileRepository, fileStoragePort);
    }

    @Test
    void findFiles_returnsContentForMatchingIds_inRequestedOrder() {
        UUID labResultId = UUID.randomUUID();
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        LabResultFile file1 = LabResultFile.create(UUID.randomUUID(), labResultId, "rapor1.jpg", "image/jpeg", 10L, "ref-1");
        LabResultFile file2 = LabResultFile.create(UUID.randomUUID(), labResultId, "rapor2.jpg", "image/jpeg", 20L, "ref-2");
        when(labResultFileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(labResultFileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        when(fileStoragePort.retrieve("ref-1")).thenReturn("icerik-1".getBytes());
        when(fileStoragePort.retrieve("ref-2")).thenReturn("icerik-2".getBytes());

        List<LabResultFileForOcr> result = adapter().findFiles(labResultId, List.of(fileId2, fileId1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fileName()).isEqualTo("rapor2.jpg");
        assertThat(result.get(0).content()).isEqualTo("icerik-2".getBytes());
        assertThat(result.get(1).fileName()).isEqualTo("rapor1.jpg");
    }

    @Test
    void findFiles_rejectsFileFromDifferentLabResult() {
        UUID labResultId = UUID.randomUUID();
        UUID otherLabResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        LabResultFile file = LabResultFile.create(UUID.randomUUID(), otherLabResultId, "baska.jpg", "image/jpeg", 10L, "ref-x");
        when(labResultFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> adapter().findFiles(labResultId, List.of(fileId)))
            .isInstanceOf(LabResultNotFoundException.class);
    }

    @Test
    void findFiles_rejectsUnknownFileId() {
        UUID labResultId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(labResultFileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter().findFiles(labResultId, List.of(fileId)))
            .isInstanceOf(LabResultNotFoundException.class);
    }
}
