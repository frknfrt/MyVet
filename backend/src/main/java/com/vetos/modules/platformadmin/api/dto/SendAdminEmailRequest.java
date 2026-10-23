package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SendAdminEmailRequest(
    @NotNull EmailTemplateType templateType,
    @NotBlank @Email String recipientEmail,
    Map<String, String> fields
) {
    public Map<String, String> fieldsOrEmpty() {
        return fields == null ? Map.of() : fields;
    }
}
