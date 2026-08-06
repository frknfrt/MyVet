package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultFileContent;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadLabResultFileUseCase {

    private final LabResultFileRepository labResultFileRepository;

    @Transactional(readOnly = true)
    public LabResultFileContent execute(UUID fileId) {
        var file = labResultFileRepository.findById(fileId)
            .orElseThrow(() -> new LabResultNotFoundException(fileId));
        return new LabResultFileContent(file.getFileName(), file.getContentType(), file.getContent());
    }
}
