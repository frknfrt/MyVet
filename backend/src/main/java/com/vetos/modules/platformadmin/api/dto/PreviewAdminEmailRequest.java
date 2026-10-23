package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PreviewAdminEmailRequest(
    @NotNull EmailTemplateType templateType,
    Map<String, String> fields
) {
    public Map<String, String> fieldsOrEmpty() {
        return fields == null ? Map.of() : fields;
    }
}
