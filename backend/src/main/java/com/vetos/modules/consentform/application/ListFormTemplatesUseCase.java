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

    /** Liste ve imzali form sayilari, sablon sayisindan bagimsiz iki sorguda okunur. */
    @Transactional(readOnly = true)
    public List<FormTemplateSummary> execute(boolean includeArchived) {
        List<FormTemplate> templates = includeArchived
            ? templateRepository.findAllByOrderByTitle()
            : templateRepository.findByStatusOrderByTitle(FormTemplateStatus.ACTIVE);
        if (templates.isEmpty()) return List.of();
        var counts = signedFormRepository.countByTemplateIds(templates.stream().map(FormTemplate::getId).toList());
        return templates.stream()
            .map(t -> new FormTemplateSummary(t.getId(), t.getTitle(), t.getStatus(), t.getUpdatedAt(),
                counts.getOrDefault(t.getId(), 0L), t.getSourceImageId() != null))
            .toList();
    }
}
