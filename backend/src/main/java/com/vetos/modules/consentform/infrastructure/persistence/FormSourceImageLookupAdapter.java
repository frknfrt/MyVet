package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.FormSourceImage;
import com.vetos.modules.consentform.domain.FormSourceImageForAi;
import com.vetos.modules.consentform.domain.FormSourceImageLookupPort;
import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import com.vetos.modules.consentform.domain.exception.FormSourceImageNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class FormSourceImageLookupAdapter implements FormSourceImageLookupPort {

    private final FormSourceImageRepository formSourceImageRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    public FormSourceImageForAi find(UUID imageId) {
        FormSourceImage image = formSourceImageRepository.findById(imageId)
            .orElseThrow(() -> new FormSourceImageNotFoundException(imageId));
        return new FormSourceImageForAi(image.getId(), image.getContentType(),
            fileStoragePort.retrieve(image.getStorageRef()));
    }
}
