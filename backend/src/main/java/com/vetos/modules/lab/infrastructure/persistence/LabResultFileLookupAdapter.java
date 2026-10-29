package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileForOcr;
import com.vetos.modules.lab.domain.LabResultFileLookupPort;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
@Component
@RequiredArgsConstructor
class LabResultFileLookupAdapter implements LabResultFileLookupPort {

    private final LabResultFileRepository labResultFileRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    public List<LabResultFileForOcr> findFiles(UUID labResultId, List<UUID> fileIds) {
        return fileIds.stream()
            .map(fileId -> toOcrFile(labResultId, fileId))
            .toList();
    }

    private LabResultFileForOcr toOcrFile(UUID labResultId, UUID fileId) {
        LabResultFile file = labResultFileRepository.findById(fileId)
            .orElseThrow(() -> new LabResultNotFoundException(fileId));
        if (!file.getLabResultId().equals(labResultId)) {
            throw new LabResultNotFoundException(fileId);
        }
        byte[] content = fileStoragePort.retrieve(file.getStorageRef());
        return new LabResultFileForOcr(file.getId(), file.getFileName(), file.getContentType(), content);
    }
}
