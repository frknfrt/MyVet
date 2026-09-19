package com.vetos.modules.lab.application;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadLabResultFileUseCase {

    private final LabResultRepository labResultRepository;
    private final LabResultFileRepository labResultFileRepository;

    @Transactional
    public UUID execute(UUID labResultId, String fileName, String contentType, byte[] content) {
        LabResult result = labResultRepository.findById(labResultId)
            .orElseThrow(() -> new LabResultNotFoundException(labResultId));

        LabResultFile file = LabResultFile.create(
            result.getTenantId(), labResultId, fileName, contentType, content
        );
        return labResultFileRepository.save(file).getId();
    }
}
