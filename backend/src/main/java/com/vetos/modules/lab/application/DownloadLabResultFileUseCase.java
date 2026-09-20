package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultFileContent;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadLabResultFileUseCase {

    private final LabResultFileRepository labResultFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public LabResultFileContent execute(UUID fileId) {
        var file = labResultFileRepository.findById(fileId)
            .orElseThrow(() -> new LabResultNotFoundException(fileId));
        byte[] content = fileStoragePort.retrieve(file.getStorageRef());
        return new LabResultFileContent(file.getFileName(), file.getContentType(), content);
    }
}
