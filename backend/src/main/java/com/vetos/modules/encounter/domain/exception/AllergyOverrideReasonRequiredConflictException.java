package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Secilen ilac hastanin alerjisiyle eslesiyor; devam etmek icin hekim gerekcesi zorunlu. */
public class AllergyOverrideReasonRequiredConflictException extends DomainException {
    public AllergyOverrideReasonRequiredConflictException(String matchedDrugs) {
        super("ALLERGY_OVERRIDE_REASON_REQUIRED",
            matchedDrugs + " hastanın kayıtlı alerjisiyle eşleşiyor. Devam etmek için gerekçe yazın.");
    }
}
