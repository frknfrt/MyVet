package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.platform.exception.DomainException;

public class MissingTemplateFieldException extends DomainException {
    public MissingTemplateFieldException(EmailTemplateType type, String fieldKey) {
        super("EMAIL_TEMPLATE_FIELD_MISSING", "Alan eksik veya bos: " + fieldKey + " (" + type.label() + ")");
    }
}
