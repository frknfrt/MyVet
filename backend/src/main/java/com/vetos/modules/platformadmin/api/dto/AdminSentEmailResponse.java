package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.AdminSentEmail;
import com.vetos.modules.platformadmin.domain.EmailTemplateType;

import java.time.Instant;
import java.util.UUID;

public record AdminSentEmailResponse(
    UUID id,
    String templateType,
    String templateLabel,
    String recipientEmail,
    String subject,
    String sentByAdminEmail,
    Instant sentAt
) {
    public static AdminSentEmailResponse from(AdminSentEmail entry) {
        String label = labelOf(entry.getTemplateType());
        return new AdminSentEmailResponse(
            entry.getId(), entry.getTemplateType(), label, entry.getRecipientEmail(),
            entry.getSubject(), entry.getSentByAdminEmail(), entry.getSentAt()
        );
    }

    private static String labelOf(String templateTypeCode) {
        try {
            return EmailTemplateType.valueOf(templateTypeCode).label();
        } catch (IllegalArgumentException e) {
            return templateTypeCode;
        }
    }
}
