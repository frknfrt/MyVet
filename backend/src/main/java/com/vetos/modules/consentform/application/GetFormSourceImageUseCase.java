package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.FormSourceImageContent;
import com.vetos.modules.consentform.domain.FormSourceImage;
import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import com.vetos.modules.consentform.domain.exception.FormSourceImageNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetFormSourceImageUseCase {

    private final FormSourceImageRepository sourceImageRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public FormSourceImageContent execute(UUID imageId) {
        FormSourceImage image = sourceImageRepository.findById(imageId)
            .orElseThrow(() -> new FormSourceImageNotFoundException(imageId));
        return new FormSourceImageContent(image.getContentType(), fileStoragePort.retrieve(image.getStorageRef()));
    }
}
