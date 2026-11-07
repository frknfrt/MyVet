package com.vetos.modules.consentform.application.dto;

import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateStatus;

import java.util.UUID;

public record FormTemplateDetail(
    UUID id, String title, FormTemplateStatus status, FormTemplateContent content, UUID sourceImageId, String clinicName
) {}
