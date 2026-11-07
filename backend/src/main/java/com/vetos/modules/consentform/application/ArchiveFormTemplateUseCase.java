package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.exception.FormTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchiveFormTemplateUseCase {

    private final FormTemplateRepository templateRepository;

    /** Yalniz status degisir; imzali formlara dokunulmaz. Zaten arsivliyse sessizce gecer. */
    @Transactional
    public void execute(UUID staffId, UUID templateId) {
        FormTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new FormTemplateNotFoundException(templateId));
        template.archive(staffId);
        templateRepository.save(template);
    }
}
