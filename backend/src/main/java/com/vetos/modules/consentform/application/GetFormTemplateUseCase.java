package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.FormTemplateDetail;
import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.exception.FormTemplateNotFoundException;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetFormTemplateUseCase {

    private final FormTemplateRepository templateRepository;
    private final TenantLookupPort tenantLookupPort;

    @Transactional(readOnly = true)
    public FormTemplateDetail execute(UUID templateId) {
        FormTemplate t = templateRepository.findById(templateId)
            .orElseThrow(() -> new FormTemplateNotFoundException(templateId));
        String clinicName = tenantLookupPort.findTenantName(t.getTenantId()).orElse("Veteriner Kliniği");
        return new FormTemplateDetail(t.getId(), t.getTitle(), t.getStatus(),
            FormTemplateContentCodec.fromJson(t.getContent()), t.getSourceImageId(), clinicName, t.contentHash());
    }
}
