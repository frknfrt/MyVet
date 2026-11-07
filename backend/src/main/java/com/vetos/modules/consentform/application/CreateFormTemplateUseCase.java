package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.FormTemplateContentValidator;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.exception.FormSourceImageNotFoundException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateFormTemplateUseCase {

    private final FormTemplateRepository templateRepository;
    private final FormSourceImageRepository sourceImageRepository;

    /** Baslik her zaman content.title() degerinden turetilir; ayri alan yok, sapma olamaz. */
    @Transactional
    public UUID execute(UUID staffId, FormTemplateContent content, UUID sourceImageId) {
        FormTemplateContentValidator.validate(content);
        if (sourceImageId != null && sourceImageRepository.findById(sourceImageId).isEmpty()) {
            throw new FormSourceImageNotFoundException(sourceImageId);
        }
        FormTemplate template = FormTemplate.create(
            TenantContext.current(), content.title(), FormTemplateContentCodec.toJson(content), sourceImageId, staffId);
        return templateRepository.save(template).getId();
    }
}
