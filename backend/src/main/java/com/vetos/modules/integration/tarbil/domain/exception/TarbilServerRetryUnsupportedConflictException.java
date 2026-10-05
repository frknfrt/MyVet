package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TarbilServerRetryUnsupportedConflictException extends DomainException {
    public TarbilServerRetryUnsupportedConflictException() {
        super("TARBIL_SERVER_RETRY_UNSUPPORTED",
            "TARBİL gönderimi klinikteki Vetly eklentisiyle hekim tarafından yapılır; sunucudan yeniden denenemez.");
    }
}
