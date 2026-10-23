package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;

import java.util.List;

public record EmailTemplateOptionResponse(String code, String label, String mailboxAddress, List<FieldOption> fields) {

    public record FieldOption(String key, String label) {
    }

    public static EmailTemplateOptionResponse from(EmailTemplateType type) {
        return new EmailTemplateOptionResponse(
            type.name(),
            type.label(),
            type.mailbox().address(),
            type.fields().stream().map(f -> new FieldOption(f.key(), f.label())).toList()
        );
    }
}
