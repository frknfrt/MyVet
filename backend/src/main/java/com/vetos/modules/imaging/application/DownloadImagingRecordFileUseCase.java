package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordFileContent;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadImagingRecordFileUseCase {

    private final ImagingRecordFileRepository imagingRecordFileRepository;

    @Transactional(readOnly = true)
    public ImagingRecordFileContent execute(UUID fileId) {
        var file = imagingRecordFileRepository.findById(fileId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(fileId));
        return new ImagingRecordFileContent(file.getFileName(), file.getContentType(), file.getContent());
    }
}
