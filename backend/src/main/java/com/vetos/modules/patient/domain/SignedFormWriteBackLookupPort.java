package com.vetos.modules.patient.domain;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** İmzalı belgenin kimliğini ve bütünlüğünü doğrular, geri yazılabilecek imzalı değerleri döndürür. */
public interface SignedFormWriteBackLookupPort {
    Map<String, Set<String>> allowedValues(UUID signedFormId, UUID patientId, UUID ownerId);

    class InvalidSourceException extends com.vetos.platform.exception.DomainException {
        public InvalidSourceException() {
            super("INVALID_FORM_WRITE_BACK_SOURCE", "Güncelleme için bu hasta ve sahibine ait geçerli, bütünlüğü doğrulanmış imzalı form gereklidir");
        }
    }
}
