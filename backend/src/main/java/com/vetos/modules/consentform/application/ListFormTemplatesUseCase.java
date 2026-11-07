package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.FormTemplateSummary;
import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.FormTemplateStatus;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListFormTemplatesUseCase {

    private final FormTemplateRepository templateRepository;
    private final SignedFormRepository signedFormRepository;

    /** Klinik basina az sayida sablon olur; sablon basina bir sayim sorgusu yeterli. */
    @Transactional(readOnly = true)
    public List<FormTemplateSummary> execute(boolean includeArchived) {
        List<FormTemplate> templates = includeArchived
            ? templateRepository.findAllByOrderByTitle()
            : templateRepository.findByStatusOrderByTitle(FormTemplateStatus.ACTIVE);
        return templates.stream()
            .map(t -> new FormTemplateSummary(t.getId(), t.getTitle(), t.getStatus(), t.getUpdatedAt(),
                signedFormRepository.countByTemplateId(t.getId()), t.getSourceImageId() != null))
            .toList();
    }
}
