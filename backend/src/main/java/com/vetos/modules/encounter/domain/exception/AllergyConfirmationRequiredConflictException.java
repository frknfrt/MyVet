package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Hastanin kritik uyarisi var ama istek uyarinin goruldugunu bildirmiyor. */
public class AllergyConfirmationRequiredConflictException extends DomainException {
    public AllergyConfirmationRequiredConflictException(String alert) {
        super("ALLERGY_CONFIRMATION_REQUIRED", "Hastanın kayıtlı uyarısı var: \"" + alert.trim() + "\". Devam etmek için uyarıyı onaylayın.");
    }
}
