package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.modules.platformadmin.domain.RenderedEmail;
import com.vetos.modules.platformadmin.infrastructure.email.EmailTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Gondermeden once admin panelinde onizleme icin -- render mantigi SendAdminEmailUseCase ile paylasilir. */
@Service
@RequiredArgsConstructor
public class PreviewAdminEmailUseCase {

    private final EmailTemplateRenderer emailTemplateRenderer;

    public RenderedEmail execute(EmailTemplateType type, Map<String, String> fields) {
        return emailTemplateRenderer.render(type, fields);
    }
}
