package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordFileContent;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadImagingRecordFileUseCase {

    private final ImagingRecordFileRepository imagingRecordFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public ImagingRecordFileContent execute(UUID fileId) {
        var file = imagingRecordFileRepository.findById(fileId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(fileId));
        byte[] content = fileStoragePort.retrieve(file.getStorageRef());
        return new ImagingRecordFileContent(file.getFileName(), file.getContentType(), content);
    }
}
