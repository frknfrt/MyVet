package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadImagingRecordFileUseCase {

    private final ImagingRecordRepository imagingRecordRepository;
    private final ImagingRecordFileRepository imagingRecordFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public UUID execute(UUID imagingRecordId, String fileName, String contentType, byte[] content) {
        ImagingRecord record = imagingRecordRepository.findById(imagingRecordId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(imagingRecordId));

        String storageRef = fileStoragePort.store(content, contentType);
        ImagingRecordFile file = ImagingRecordFile.create(
            record.getTenantId(), imagingRecordId, fileName, contentType, content.length, storageRef
        );
        return imagingRecordFileRepository.save(file).getId();
    }
}
