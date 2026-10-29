package com.vetos.modules.imaging.infrastructure.persistence;

import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileForOcr;
import com.vetos.modules.imaging.domain.ImagingRecordFileLookupPort;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
@Component
@RequiredArgsConstructor
class ImagingRecordFileLookupAdapter implements ImagingRecordFileLookupPort {

    private final ImagingRecordFileRepository imagingRecordFileRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    public List<ImagingRecordFileForOcr> findFiles(UUID imagingRecordId, List<UUID> fileIds) {
        return fileIds.stream()
            .map(fileId -> toOcrFile(imagingRecordId, fileId))
            .toList();
    }

    private ImagingRecordFileForOcr toOcrFile(UUID imagingRecordId, UUID fileId) {
        ImagingRecordFile file = imagingRecordFileRepository.findById(fileId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(fileId));
        if (!file.getImagingRecordId().equals(imagingRecordId)) {
            throw new ImagingRecordNotFoundException(fileId);
        }
        byte[] content = fileStoragePort.retrieve(file.getStorageRef());
        return new ImagingRecordFileForOcr(file.getId(), file.getFileName(), file.getContentType(), content);
    }
}
