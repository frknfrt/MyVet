package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadImagingRecordFileUseCase {

    private final ImagingRecordRepository imagingRecordRepository;
    private final ImagingRecordFileRepository imagingRecordFileRepository;

    @Transactional
    public UUID execute(UUID imagingRecordId, String fileName, String contentType, byte[] content) {
        imagingRecordRepository.findById(imagingRecordId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(imagingRecordId));

        ImagingRecordFile file = ImagingRecordFile.create(imagingRecordId, fileName, contentType, content);
        return imagingRecordFileRepository.save(file).getId();
    }
}
