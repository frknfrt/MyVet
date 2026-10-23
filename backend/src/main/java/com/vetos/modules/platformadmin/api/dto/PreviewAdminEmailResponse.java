package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.RenderedEmail;

public record PreviewAdminEmailResponse(String subject, String html) {
    public static PreviewAdminEmailResponse from(RenderedEmail rendered) {
        return new PreviewAdminEmailResponse(rendered.subject(), rendered.html());
    }
}
