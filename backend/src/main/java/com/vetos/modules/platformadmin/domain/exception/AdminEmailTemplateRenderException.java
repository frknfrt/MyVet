package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Render sonrasi hala {{...}} tokeni kalmissa (sablon/alan anahtari uyusmazligi) -- bkz. EmailTemplateRenderer. */
public class AdminEmailTemplateRenderException extends DomainException {
    public AdminEmailTemplateRenderException(String unresolvedToken) {
        super("EMAIL_TEMPLATE_RENDER_FAILED", "Sablon render edilemedi, cozulmemis alan: " + unresolvedToken);
    }
}
