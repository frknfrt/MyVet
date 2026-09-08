package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AssessmentRequiredForRecommendationException extends DomainException {
    public AssessmentRequiredForRecommendationException(UUID encounterId) {
        super("ASSESSMENT_REQUIRED_FOR_RECOMMENDATION",
            "Tedavi onerisi icin once Assessment alani doldurulup kaydedilmeli: " + encounterId);
    }
}
