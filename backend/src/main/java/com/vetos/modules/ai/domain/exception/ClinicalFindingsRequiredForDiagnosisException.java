package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class ClinicalFindingsRequiredForDiagnosisException extends DomainException {
    public ClinicalFindingsRequiredForDiagnosisException(UUID encounterId) {
        super("CLINICAL_FINDINGS_REQUIRED_FOR_DIAGNOSIS",
            "Tani destegi icin once Subjective veya Objective alani doldurulup kaydedilmeli: " + encounterId);
    }
}
