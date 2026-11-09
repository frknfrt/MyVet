package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.FormTemplateContentValidator;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.FormTemplateStatus;
import com.vetos.modules.consentform.domain.exception.FormTemplateArchivedException;
import com.vetos.modules.consentform.domain.exception.FormTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateFormTemplateUseCase {

    private final FormTemplateRepository templateRepository;

    /** Imzali formlar kendi snapshot kopyasini tasir; sablonu degistirmek onlari etkilemez. */
    @Transactional
    public void execute(UUID staffId, UUID templateId, FormTemplateContent content) {
        FormTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new FormTemplateNotFoundException(templateId));
        if (template.getStatus() == FormTemplateStatus.ARCHIVED) {
            throw new FormTemplateArchivedException();
        }
        FormTemplateContentValidator.validate(content);
        if (content.privacyNotice() != null) throw new com.vetos.modules.consentform.domain.exception.InvalidFormTemplateException("Şablona imzalı KVKK beyanı eklenemez");
        template.update(content.title(), FormTemplateContentCodec.toJson(content), staffId);
        templateRepository.save(template);
    }
}
