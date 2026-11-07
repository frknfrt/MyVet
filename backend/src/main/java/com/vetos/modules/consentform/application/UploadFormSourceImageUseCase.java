package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.FormSourceImage;
import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import com.vetos.modules.consentform.domain.exception.UnsupportedFormSourceImageException;
import com.vetos.modules.consentform.domain.FormImageFormat;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadFormSourceImageUseCase {

    static final int MAX_BYTES = 10 * 1024 * 1024;

    private final FormSourceImageRepository sourceImageRepository;
    private final FileStoragePort fileStoragePort;

    /** Yeni gorselin id degerini doner. Tur, dosya adina degil magic byte a gore belirlenir. */
    @Transactional
    public UUID execute(UUID staffId, byte[] content) {
        if (content == null || content.length > MAX_BYTES) {
            throw new UnsupportedFormSourceImageException();
        }
        String contentType = FormImageFormat.detectContentType(content)
            .orElseThrow(UnsupportedFormSourceImageException::new);
        String ref = fileStoragePort.store(content, contentType);
        return sourceImageRepository.save(
            FormSourceImage.create(TenantContext.current(), ref, contentType, staffId)).getId();
    }
}
